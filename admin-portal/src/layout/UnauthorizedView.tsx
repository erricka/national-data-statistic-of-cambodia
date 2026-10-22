import React from "react";
import Icon from "../icons/Icon"; // Adjust path to match your Icon component

export const UnauthorizedView: React.FC = () => {
    return (
        <div style={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            height: "70vh",
            textAlign: "center",
            padding: "20px"
        }}>
            {/* Red Circle Icon Container */}
            <div style={{
                width: "80px",
                height: "80px",
                borderRadius: "50%",
                backgroundColor: "#FEE2E2",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                marginBottom: "24px"
            }}>
                <div style={{
                    width: "56px",
                    height: "56px",
                    borderRadius: "50%",
                    backgroundColor: "#FCA5A5",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center"
                }}>
                    <Icon name="slash" />
                </div>
            </div>

            <span style={{
                color: "#DC2626",
                fontWeight: 600,
                fontSize: "14px",
                letterSpacing: "0.5px",
                marginBottom: "8px"
            }}>
                403
            </span>

            <h2 style={{
                fontSize: "24px",
                fontWeight: 700,
                color: "#1E293B",
                margin: "0 0 12px 0"
            }}>
                Unauthorized
            </h2>

            <p style={{
                color: "#64748B",
                fontSize: "14px",
                maxWidth: "420px",
                lineHeight: "1.5",
                margin: 0
            }}>
                You don't have permission to access this resource. Please contact your administrator if you believe this is a mistake.
            </p>
        </div>
    );
};