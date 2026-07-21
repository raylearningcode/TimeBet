/**
 * Supabase Edge Function: refresh-fixtures
 *
 * Fetches upcoming football fixtures + odds from API-Football and caches them
 * in the sports_fixtures and sports_odds tables.
 *
 * Schedule: Run every 15 minutes via Supabase Cron / pg_cron.
 * Trigger: supabase.com/dashboard → Edge Functions → refresh-fixtures → schedule
 *
 * PRD Section 37.1: API keys remain server-side — never exposed to the Android client.
 */

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// ─── Configuration ───

const API_FOOTBALL_KEY = Deno.env.get("API_FOOTBALL_KEY")!;
const API_FOOTBALL_BASE = "https://v3.football.api-sports.io";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

// ─── Types ───

interface ApiFixtureResponse {
    fixture: {
        id: number;
        date: string;         // ISO timestamp
        status: { short: string }; // NS, TBD, 1H, HT, 2H, FT, PST, CANC, etc.
    };
    league: {
        id: number;
        name: string;
    };
    teams: {
        home: { name: string };
        away: { name: string };
    };
    goals: {
        home: number | null;
        away: number | null;
    };
}

interface ApiOddsResponse {
    fixture: { id: number };
    bookmakers: Array<{
        bets: Array<{
            name: string;       // "Match Winner", "Over/Under", "Both Teams Score"
            values: Array<{
                value: string;  // "Home", "Draw", "Away", "Over 1.5", "Yes", etc.
                odd: string;    // "2.10"
            }>;
        }>;
    }>;
}

// ─── Helpers ───

function delay(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

const MARKET_MAP: Record<string, string> = {
    "Match Winner": "home_draw_away",
    "Over/Under": "over_under",       // Will be combined with value for 1.5 / 2.5
    "Both Teams Score": "both_teams_to_score",
};

function statusFromApi(short: string): string {
    switch (short) {
        case "NS": case "TBD": return "scheduled";
        case "1H": case "HT": case "2H": case "ET": case "P": case "LIVE": return "live";
        case "FT": case "AET": case "PEN": return "finished";
        case "CANC": case "PST": case "ABD": return "cancelled";
        default: return "scheduled";
    }
}

function extractMarketType(betName: string, value: string): { marketType: string; selection: string } | null {
    // Match Winner → home_draw_away
    if (betName === "Match Winner") {
        const sel = value === "Home" ? "home" : value === "Draw" ? "draw" : "away";
        return { marketType: "home_draw_away", selection: sel };
    }

    // Over/Under → over_under_1_5 or over_under_2_5
    if (betName === "Over/Under") {
        if (value === "Over 1.5" || value === "Under 1.5") {
            return {
                marketType: "over_under_1_5",
                selection: value.startsWith("Over") ? "over" : "under",
            };
        }
        if (value === "Over 2.5" || value === "Under 2.5") {
            return {
                marketType: "over_under_2_5",
                selection: value.startsWith("Over") ? "over" : "under",
            };
        }
    }

    // Both Teams Score → both_teams_to_score
    if (betName === "Both Teams Score") {
        return {
            marketType: "both_teams_to_score",
            selection: value === "Yes" ? "yes" : "no",
        };
    }

    return null;
}

// ─── API Calls ───

async function fetchFixturesByDate(date: string): Promise<ApiFixtureResponse[]> {
    const url = `${API_FOOTBALL_BASE}/fixtures?date=${date}`;

    const res = await fetch(url, {
        headers: {
            "x-apisports-key": API_FOOTBALL_KEY,
        },
    });

    if (!res.ok) {
        throw new Error(`API-Football fixtures error: ${res.status} ${await res.text()}`);
    }

    const json = await res.json();
    return json.response ?? [];
}

async function fetchOdds(fixtureId: number): Promise<ApiOddsResponse | null> {
    const url = `${API_FOOTBALL_BASE}/odds?fixture=${fixtureId}`;

    const res = await fetch(url, {
        headers: {
            "x-apisports-key": API_FOOTBALL_KEY,
        },
    });

    if (!res.ok) {
        console.warn(`Skipping odds for fixture ${fixtureId}: ${res.status}`);
        return null;
    }

    const json = await res.json();
    return json.response?.[0] ?? null;
}

// ─── Main ───

// ─── Major League + Team Filters ───
// API-Football league IDs for big competitions + major club names for friendlies.

const MAJOR_LEAGUE_IDS = new Set([
    // England
    39,   // Premier League
    45,   // FA Cup
    48,   // EFL Cup (Carabao Cup)
    // Europe
    2,    // UEFA Champions League
    3,    // UEFA Europa League
    848,  // UEFA Europa Conference League
    // Spain
    140,  // La Liga
    // Italy
    135,  // Serie A
    // Germany
    78,   // Bundesliga
    // France
    61,   // Ligue 1
    // International
    1,    // FIFA World Cup
    4,    // UEFA European Championship
    15,   // FIFA Club World Cup
    5,    // UEFA Nations League
]);

function isWorthShowing(f: ApiFixtureResponse): boolean {
    // Only show major league/cup fixtures — no friendlies.
    return MAJOR_LEAGUE_IDS.has(f.league.id);
}

async function refreshFixtures() {
    // Clean up old friendlies that may have been cached before the filter changed
    const { error: deleteErr } = await supabase
        .from("sports_fixtures")
        .delete()
        .ilike("competition", "%friendlies%");
    if (deleteErr) {
        console.warn("Failed to clean up friendlies:", deleteErr.message);
    } else {
        console.log("Cleaned up old friendlies from cache");
    }

    // Fetch today + next 5 days for upcoming fixtures, plus yesterday/today
    // for status updates on recently finished matches.
    const dates: string[] = [];
    const now = new Date();
    // Include yesterday to catch recently finished matches
    const yesterday = new Date(now);
    yesterday.setDate(yesterday.getDate() - 1);
    dates.push(yesterday.toISOString().split("T")[0]);
    // Today + next 5 days for upcoming
    for (let d = 0; d <= 5; d++) {
        const date = new Date(now);
        date.setDate(date.getDate() + d);
        dates.push(date.toISOString().split("T")[0]);
    }

    let scheduledCount = 0;
    const MAX_ODDS_FETCHES = 10;

    for (const date of dates) {
        console.log(`Fetching ${date}...`);
        const fixtures = await fetchFixturesByDate(date);
        console.log(`Got ${fixtures.length} total fixtures`);

        // Filter to major leagues only — no friendlies
        const majorFixtures = fixtures.filter((f) => isWorthShowing(f));
        console.log(`${majorFixtures.length} worth showing`);

        for (const f of majorFixtures) {
            const fixtureId = f.fixture.id.toString();
            const status = statusFromApi(f.fixture.status.short);

            const { error: fixtureErr } = await supabase
                .from("sports_fixtures")
                .upsert({
                    id: fixtureId,
                    competition: f.league.name,
                    home_team: f.teams.home.name,
                    away_team: f.teams.away.name,
                    kickoff_time: f.fixture.date,
                    status,
                    home_score: f.goals.home,
                    away_score: f.goals.away,
                    updated_at: new Date().toISOString(),
                });

            if (fixtureErr) continue;

            // Fetch odds for scheduled major-league fixtures
            if (status === "scheduled" && scheduledCount < MAX_ODDS_FETCHES) {
                scheduledCount++;
                console.log(`Odds: ${f.teams.home.name} vs ${f.teams.away.name} (${f.league.name})`);
                try {
                    const oddsData = await fetchOdds(f.fixture.id);
                    if (oddsData?.bookmakers?.length) {
                        const bookmaker = oddsData.bookmakers.find((b) =>
                            b.bets.some((bet) => bet.name in MARKET_MAP)
                        ) ?? oddsData.bookmakers[0];

                        for (const bet of bookmaker.bets) {
                            for (const val of bet.values) {
                                const mapped = extractMarketType(bet.name, val.value);
                                if (!mapped) continue;
                                await supabase.from("sports_odds").upsert({
                                    fixture_id: fixtureId,
                                    market_type: mapped.marketType,
                                    selection: mapped.selection,
                                    odds: parseFloat(val.odd),
                                    updated_at: new Date().toISOString(),
                                });
                            }
                        }
                    }
                } catch (e) {
                    console.warn(`Odds failed for ${fixtureId}: ${e}`);
                }
            }
        }
    }

    console.log(`Done. Stored major-league fixtures, fetched odds for ${scheduledCount} matches.`);
}

// ─── Entry Point ───

Deno.serve(async (_req: Request) => {
    try {
        await refreshFixtures();
        return new Response(JSON.stringify({ success: true }), {
            status: 200,
            headers: { "Content-Type": "application/json" },
        });
    } catch (err) {
        console.error("Refresh failed:", err);
        return new Response(JSON.stringify({ error: String(err) }), {
            status: 500,
            headers: { "Content-Type": "application/json" },
        });
    }
});
