import React from "react";
import Sidebar from "./Sidebar";
import Topbar from "./Topbar";
import { UnauthorizedView } from "./UnauthorizedView";

interface AdminPageContainerProps {
    sidebarActive: string;
    pageTitle: string;
    hasInitialLoaded: boolean;
    isLoading: boolean;
    isForbidden: boolean;
    children: React.ReactNode;
}

export const AdminPageContainer: React.FC<AdminPageContainerProps> = ({
    sidebarActive,
    pageTitle,
    hasInitialLoaded,
    isLoading,
    isForbidden,
    children,
}) => {
    return (
        <div className="app">
            <Sidebar active={sidebarActive} />
            <div className="main">
                <Topbar pageTitle={pageTitle} />
                <main className="content">
                    {!hasInitialLoaded && isLoading ? (
                        <div
                            style={{
                                display: "flex",
                                justifyContent: "center",
                                alignItems: "center",
                                minHeight: "calc(100vh - 140px)",
                                color: "#64748b",
                                fontSize: "14px",
                            }}
                        >
                            <span>Loading...</span>
                        </div>
                    ) : isForbidden ? (
                        <UnauthorizedView />
                    ) : (
                        children
                    )}
                </main>
            </div>
        </div>
    );
};