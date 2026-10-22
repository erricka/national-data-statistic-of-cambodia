import { useState, useCallback, useRef, useEffect } from "react";

export function usePermissionGuard<T>(
    fetcherFn: () => Promise<any>,
    dataExtractor: (res: any) => { items: T[]; total: number; totalPages?: number }
) {
    const [data, setData] = useState<T[]>([]);
    const [totalItems, setTotalItems] = useState<number>(0);
    const [totalPages, setTotalPages] = useState<number>(1);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [isForbidden, setIsForbidden] = useState<boolean>(false);
    const [hasInitialLoaded, setHasInitialLoaded] = useState<boolean>(false);
    const [errorMessage, setErrorMessage] = useState<string>("");

    // Keep active refs for callbacks to prevent infinite dependency loops
    const fetcherRef = useRef(fetcherFn);
    const extractorRef = useRef(dataExtractor);

    useEffect(() => {
        fetcherRef.current = fetcherFn;
        extractorRef.current = dataExtractor;
    }, [fetcherFn, dataExtractor]);

    const executeFetch = useCallback(async () => {
        setIsLoading(true);
        setErrorMessage("");

        try {
            const response = await fetcherRef.current();

            // 1. Check for custom backend wrapper codes
            if (response?.code === "403" || response?.code === "999998") {
                setIsForbidden(true);
                return;
            }

            // 2. Handle Successful Data Response
            if (response?.code === "000000" && response.data) {
                const extracted = extractorRef.current(response);
                setData(extracted.items || []);
                setTotalItems(extracted.total || 0);
                setTotalPages(extracted.totalPages || 1);
                setIsForbidden(false);
            } else {
                setData([]);
                const msg = response?.message;
                setErrorMessage(typeof msg === "string" ? msg : msg?.messageBody || "Failed to load data.");
            }
        } catch (error: any) {
            console.error("Fetch failed:", error);

            const httpStatus = error.response?.status || error.response?.data?.status;
            const errorTitle = error.response?.data?.error;

            if (httpStatus === 403 || errorTitle === "Forbidden" || error.code === "403") {
                setIsForbidden(true);
            } else {
                setData([]);
                setErrorMessage("Error connecting to server.");
            }
        } finally {
            setIsLoading(false);
            setHasInitialLoaded(true);
        }
    }, []); // Empty dependency array prevents re-creation loop!

    return {
        data,
        totalItems,
        totalPages,
        isLoading,
        isForbidden,
        hasInitialLoaded,
        errorMessage,
        executeFetch,
    };
}