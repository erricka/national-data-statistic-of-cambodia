/**
 * Parses any error (Axios error, standard Error, or custom object)
 * into a clean, human-readable string message.
 */
export const getErrorMessage = (err: any): string => {
    if (!err) return "An unexpected error occurred. Please try again.";

    // 1. Check message attached by apiClient response interceptor or standard JS error
    if (typeof err === "string") return err;

    // 2. Business error standard object message
    const msg = err?.response?.data?.message ?? err?.message ?? err;

    if (typeof msg === "object" && msg !== null) {
        return msg.messageBody || msg.messageDebug || "Operation failed.";
    }

    return typeof msg === "string" ? msg : "An unexpected error occurred. Please try again.";
};