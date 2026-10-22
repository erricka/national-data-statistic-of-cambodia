import { useNavigate } from "react-router-dom";
import Icon from "../icons/Icon";
import "../index.css";
import { logoutApi } from "../service/auth/authService"; // Adjust import path to match your structure

interface TopbarProps {
    pageTitle?: string;
}

export default function Topbar({ pageTitle = "" }: TopbarProps) {
    const navigate = useNavigate();

    const toggleSidebar = () => {
        document.body.classList.toggle("nav-open");
    };

    const handleLogout = async () => {
        try {
            await logoutApi();
        } catch (error) {
            console.error("Logout request failed:", error);
        } finally {

            localStorage.removeItem("token");
            localStorage.clear();
            sessionStorage.clear();

            navigate("/login");
        }
    };

    return (
        <header className="topbar">
            <button
                className="icon-btn hamburger"
                aria-label="Open Menu"
                onClick={toggleSidebar}
            >
                <Icon name="menu" />
            </button>

            <div className="crumb">
                Home <span className="sep">/</span> <b>{pageTitle}</b>
            </div>

            <div className="topbar-spacer"></div>

            <button
                className="icon-btn"
                aria-label="Change Password"
                title="Change Password"
                onClick={() => navigate("/admin/change-password")}
            >
                <Icon name="key" />
            </button>

            <button
                className="icon-btn"
                aria-label="Sign Out"
                title="Sign Out"
                onClick={handleLogout}
                style={{ color: "var(--danger, #ef4444)" }}
            >
                <Icon name="logout" />
            </button>
        </header>
    );
}