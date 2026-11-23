package app.phueber.seniorlauncher;

/**
 * Who may lock and unlock through {@link LockProvider} without the PIN, and
 * what the answer is: only SeniorNav, whose volume-button pattern (set up by
 * the helper) stands in for the PIN. A sanity check on the calling package,
 * not security: like the PIN itself (see Pin), the lock keeps a curious
 * senior out, not a determined person or app. Pure, so it is unit tested.
 */
final class NavLock {
    static final String SENIORNAV = "app.phueber.seniornav";

    /** Why a call was refused, or OK; the provider answers it as "error" (null for OK). */
    enum Result {
        OK(null),
        /** Not SeniorNav. */
        NOT_ALLOWED("not_allowed"),
        /** No PIN was ever set: locking would leave nobody able to unlock with one. */
        NO_PIN("no_pin");

        final String error;

        Result(String error) {
            this.error = error;
        }
    }

    private NavLock() {}

    static boolean allowed(String callingPackage) {
        return SENIORNAV.equals(callingPackage);
    }

    static Result lock(String callingPackage, boolean hasPin) {
        if (!allowed(callingPackage)) return Result.NOT_ALLOWED;
        return hasPin ? Result.OK : Result.NO_PIN;
    }

    static Result unlock(String callingPackage) {
        return allowed(callingPackage) ? Result.OK : Result.NOT_ALLOWED;
    }
}
