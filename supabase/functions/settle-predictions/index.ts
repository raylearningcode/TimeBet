/**
 * Supabase Edge Function: settle-predictions
 *
 * Checks finished fixtures against pending predictions and settles them.
 * The client must never be trusted to declare a prediction won (PRD §36).
 *
 * Schedule: Run every 5 minutes via Supabase Cron.
 */

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

interface Prediction {
    id: string;
    provider_event_id: string;
    market_type: string;
    selection: string;
    stake_seconds: number;
    potential_profit_seconds: number;
    odds_at_placement: number;
}

interface Fixture {
    id: string;
    status: string;
    home_score: number | null;
    away_score: number | null;
}

// ─── Settlement Logic ───

function determineOutcome(
    prediction: Prediction,
    fixture: Fixture
): { status: "won" | "lost" | "void"; profit: number } {
    const home = fixture.home_score ?? 0;
    const away = fixture.away_score ?? 0;
    const totalGoals = home + away;

    switch (prediction.market_type) {
        case "home_draw_away": {
            const actualResult = home > away ? "home" : home < away ? "away" : "draw";
            if (actualResult === prediction.selection) {
                return { status: "won", profit: prediction.potential_profit_seconds };
            }
            return { status: "lost", profit: 0 };
        }

        case "over_under_1_5": {
            if (prediction.selection === "over" && totalGoals > 1.5) {
                return { status: "won", profit: prediction.potential_profit_seconds };
            }
            if (prediction.selection === "under" && totalGoals < 1.5) {
                return { status: "won", profit: prediction.potential_profit_seconds };
            }
            if (totalGoals === 1.5) {
                // Exact push at half-goal lines isn't possible with integer goals,
                // but included for safety
                return { status: "void", profit: 0 };
            }
            return { status: "lost", profit: 0 };
        }

        case "over_under_2_5": {
            if (prediction.selection === "over" && totalGoals > 2.5) {
                return { status: "won", profit: prediction.potential_profit_seconds };
            }
            if (prediction.selection === "under" && totalGoals < 2.5) {
                return { status: "won", profit: prediction.potential_profit_seconds };
            }
            return { status: "lost", profit: 0 };
        }

        case "both_teams_to_score": {
            const bothScored = home > 0 && away > 0;
            if (prediction.selection === "yes" && bothScored) {
                return { status: "won", profit: prediction.potential_profit_seconds };
            }
            if (prediction.selection === "no" && !bothScored) {
                return { status: "won", profit: prediction.potential_profit_seconds };
            }
            return { status: "lost", profit: 0 };
        }

        default:
            return { status: "void", profit: 0 };
    }
}

// ─── Main ───

async function settlePredictions() {
    // 1. Get all finished fixtures
    const { data: finishedFixtures, error: fixtureErr } = await supabase
        .from("sports_fixtures")
        .select("id, status, home_score, away_score")
        .eq("status", "finished");

    if (fixtureErr) {
        throw new Error(`Failed to fetch finished fixtures: ${fixtureErr.message}`);
    }

    if (!finishedFixtures?.length) {
        console.log("No finished fixtures to process.");
        return;
    }

    const finishedIds = finishedFixtures.map((f) => f.id);

    // 2. Get pending predictions for those fixtures
    const { data: pendingPredictions, error: predErr } = await supabase
        .from("sports_predictions")
        .select("*")
        .in("provider_event_id", finishedIds)
        .in("status", ["pending_cancelable", "pending_locked"]);

    if (predErr) {
        throw new Error(`Failed to fetch predictions: ${predErr.message}`);
    }

    if (!pendingPredictions?.length) {
        console.log("No pending predictions to settle.");
        return;
    }

    console.log(`Settling ${pendingPredictions.length} predictions...`);

    // 3. Settle each prediction
    for (const prediction of pendingPredictions) {
        const fixture = finishedFixtures.find((f) => f.id === prediction.provider_event_id);
        if (!fixture) continue;

        const outcome = determineOutcome(prediction, fixture);

        const { error: updateErr } = await supabase
            .from("sports_predictions")
            .update({
                status: outcome.status,
                settled_at: new Date().toISOString(),
                settlement_profit_seconds: outcome.profit,
            })
            .eq("id", prediction.id);

        if (updateErr) {
            console.error(`Failed to settle prediction ${prediction.id}:`, updateErr);
        } else {
            console.log(
                `  ${prediction.id}: ${prediction.market_type}/${prediction.selection} → ${outcome.status} (+${outcome.profit}s)`
            );
        }
    }

    // 4. Handle voided/cancelled fixtures (postponed, abandoned)
    const { data: cancelledFixtures } = await supabase
        .from("sports_fixtures")
        .select("id")
        .in("status", ["cancelled", "postponed"]);

    if (cancelledFixtures?.length) {
        const cancelledIds = cancelledFixtures.map((f) => f.id);

        const { error: voidErr } = await supabase
            .from("sports_predictions")
            .update({
                status: "void",
                settled_at: new Date().toISOString(),
                settlement_profit_seconds: 0,
            })
            .in("provider_event_id", cancelledIds)
            .in("status", ["pending_cancelable", "pending_locked"]);

        if (voidErr) {
            console.error("Failed to void cancelled predictions:", voidErr);
        }
    }

    console.log("Settlement complete.");
}

// ─── Entry Point ───

Deno.serve(async (_req: Request) => {
    try {
        await settlePredictions();
        return new Response(JSON.stringify({ success: true }), {
            status: 200,
            headers: { "Content-Type": "application/json" },
        });
    } catch (err) {
        console.error("Settlement failed:", err);
        return new Response(JSON.stringify({ error: String(err) }), {
            status: 500,
            headers: { "Content-Type": "application/json" },
        });
    }
});
