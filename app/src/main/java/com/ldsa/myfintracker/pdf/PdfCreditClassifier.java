package com.ldsa.myfintracker.pdf;

/**
 * Decides whether each extracted PDF row is credit or debit.
 *
 * Fed one row at a time in document (page) order. In auto-credit mode the
 * decision comes from the sign of the balance delta vs the previous row:
 *   current - previous > 0  → credit (deposit)
 *   current - previous < 0  → debit  (expense)
 *   current - previous = 0  → skip (zero-delta rows get dropped)
 *
 * Fallbacks:
 *  - Auto-credit off → use the legacy rule (non-empty /amount_cr/ match = credit).
 *  - Auto-credit on but the first row has no previous balance → legacy rule for that one row.
 *  - Auto-credit on but the pattern didn't capture /balance/ on this row →
 *    legacy rule (defensive; the BankConfigActivity UI prevents this by hiding
 *    the toggle when no pattern has /balance/).
 *
 * Not thread-safe; one instance per statement scan.
 */
public class PdfCreditClassifier {

    public static class Decision {
        public boolean isCredit;
        public boolean skip;
    }

    private static final double EPSILON = 0.01;

    private final boolean autoCreditOn;
    private boolean havePrev;
    private double  prevBalance;

    public PdfCreditClassifier(boolean autoCreditOn) {
        this.autoCreditOn = autoCreditOn;
    }

    public boolean isAutoCreditOn() { return autoCreditOn; }

    /** @param hasBalance      whether the pattern captured /balance/ for this row
     *  @param balance         extracted balance (ignored when hasBalance == false)
     *  @param amountCrMatched whether the pattern captured a non-empty /amount_cr/
     *                         (used for the legacy fallback) */
    public Decision classify(boolean hasBalance, double balance, boolean amountCrMatched) {
        Decision d = new Decision();
        if (!autoCreditOn || !hasBalance) {
            d.isCredit = amountCrMatched;
            if (autoCreditOn && hasBalance) {
                havePrev = true;
                prevBalance = balance;
            }
            return d;
        }
        if (!havePrev) {
            // First row — nothing to diff against. Fall back to legacy for this one row.
            d.isCredit = amountCrMatched;
            havePrev = true;
            prevBalance = balance;
            return d;
        }
        double delta = balance - prevBalance;
        prevBalance = balance;
        if (Math.abs(delta) < EPSILON) {
            d.skip = true;
            return d;
        }
        d.isCredit = delta > 0;
        return d;
    }
}
