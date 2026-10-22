import { getErrorMessage } from "../../../utils/errorHandler";

interface ErrorLike {
    isBusinessError?: boolean;
    code?: string;
    response?: { status?: number };
    message?: unknown;
}

const asErrorLike = (err: unknown): ErrorLike =>
    typeof err === "object" && err !== null ? (err as ErrorLike) : {};

/** True when the backend rejected the call for authorization reasons. */
export const isForbiddenError = (err: unknown): boolean => {
    const e = asErrorLike(err);
    return e.response?.status === 403 || e.code === "403" || e.code === "999998";
};

/**
 * Converts any approval API failure into a message safe to show users.
 * Business errors keep the backend's message (via the existing getErrorMessage);
 * HTTP/network failures get a friendly message instead of raw Axios text.
 */
export const getApprovalErrorMessage = (err: unknown, fallback: string): string => {
    const e = asErrorLike(err);

    if (e.isBusinessError) {
        if (isForbiddenError(err)) return "You don't have permission to perform this action.";
        const msg = getErrorMessage(err);
        return msg || fallback;
    }

    const httpStatus = e.response?.status;
    if (httpStatus === 401) return "Your session has expired. Please sign in again.";
    if (httpStatus === 403) return "You don't have permission to perform this action.";
    if (httpStatus === 404) return "The requested upload could not be found.";
    if (httpStatus && httpStatus >= 500) return "The server encountered an error. Please try again later.";
    if (e.code === "ECONNABORTED") return "The request timed out. Please try again.";
    if (e.code === "ERR_NETWORK") {
        return "Unable to connect to the server. Please check your connection and try again.";
    }

    const msg = getErrorMessage(err);
    return !msg || msg.startsWith("Request failed with status code") ? fallback : msg;
};
