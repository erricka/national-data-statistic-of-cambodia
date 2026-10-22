// Standard backend error message object structure
export interface ApiErrorMessage {
    messageTitle: string;
    messageBody: string;
    messageDebug?: string;
}

// Standard generic API Response structure
export interface ApiResponse<T = unknown> {
    code: string;
    message: string | ApiErrorMessage;
    data?: T;
}