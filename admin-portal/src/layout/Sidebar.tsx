import { useState, useEffect } from "react";
import Icon from "../icons/Icon";
import "../index.css";
import { NavLink, useNavigate } from "react-router-dom";
import logo from "../assets/logo.jpg";

interface UserInfo {
    firstName?: string;
    lastName?: string;
    email?: string;
    userType?: string;
}

export default function Sidebar({ active }: { active: string }) {
    const navigate = useNavigate();
    const [user, setUser] = useState<UserInfo | null>(null);

    useEffect(() => {
        const storedUser = localStorage.getItem("user");
        if (storedUser) {
            try {
                setUser(JSON.parse(storedUser));
            } catch (e) {
                console.error("Failed to parse stored user info", e);
            }
        }
    }, []);

    const closeSidebar = () => {
        document.body.classList.remove("nav-open");
    };

    const handleNavigate = (path: string) => {
        closeSidebar();
        navigate(path);
    };

    const firstName = user?.firstName || "";
    const lastName = user?.lastName || "";
    const fullName = `${firstName} ${lastName}`.trim() || "System User";
    const email = user?.email || "No email available";

    const getInitials = () => {
        if (firstName && lastName) {
            return `${firstName.charAt(0)}${lastName.charAt(0)}`.toUpperCase();
        }
        if (firstName) return firstName.substring(0, 2).toUpperCase();
        return "SU";
    };

    return (
        <>
            {/* Overlay backdrop to close sidebar on mobile */}
            <div className="backdrop" onClick={closeSidebar}></div>

            <aside className="sidebar">
                {/* Brand Logo & Name */}
                <div
                    className="sidebar-brand"
                    style={{ cursor: "pointer" }}
                    onClick={() => handleNavigate("/")}
                >
                    <div className="brand-mark">
                        <img src={logo} alt="National Institute of Statistics Logo" />
                    </div>

                    <div className="brand-txt">
                        <b> National Institute of Statistics</b>
                    </div>
                </div>

                {/* Navigation Menu */}
                <nav className="nav">
                    {/* Overview Group */}
                    <div className="nav-group">
                        <h6>Overview</h6>

                        <NavLink
                            to="/admin/dashboard"
                            onClick={closeSidebar}
                            className={`nav-item ${active === "dashboard" ? "active" : ""}`}
                        >
                            <Icon name="grid" />
                            <span>Dashboard</span>
                        </NavLink>
                    </div>

                    {/* Data Management Group */}
                    <div className="nav-group">
                        <h6>Data Management</h6>

                        <NavLink
                            to="/admin/category-management"
                            onClick={closeSidebar}
                            className={`nav-item ${active === "category_management" ? "active" : ""}`}
                        >
                            <Icon name="category" />
                            <span>Category</span>
                        </NavLink>

                        <NavLink
                            to="/admin/data-management"
                            onClick={closeSidebar}
                            className={`nav-item ${active === "data_management" ? "active" : ""}`}
                        >
                            <Icon name="database" />
                            <span>Dataset</span>
                        </NavLink>
                    </div>

                    {/* Governance Group */}
                    <div className="nav-group">
                        <h6>Governance</h6>

                        <NavLink
                            to="/admin/user-management"
                            onClick={closeSidebar}
                            className={`nav-item ${active === "user_management" ? "active" : ""}`}
                        >
                            <Icon name="users" />
                            <span>User Management</span>
                        </NavLink>

                        <NavLink
                            to="/admin/role-management"
                            onClick={closeSidebar}
                            className={`nav-item ${active === "role_management" ? "active" : ""}`}
                        >
                            <Icon name="shield" />
                            <span>Roles Management</span>
                        </NavLink>

                        <NavLink
                            to="/admin/permission-management"
                            onClick={closeSidebar}
                            className={`nav-item ${active === "permission_management" ? "active" : ""}`}
                        >
                            <Icon name="permission" />
                            <span>Permission Management</span>
                        </NavLink>
                        <NavLink
                            to="/admin/approvals"
                            onClick={closeSidebar}
                            className={`nav-item ${active === "approval_management" ? "active" : ""}`}
                        >
                            <Icon name="check" />
                            <span>Approval Management</span>
                        </NavLink>
                    </div>
                </nav>
                

                {/* Sidebar Footer / Dynamic User Profile */}
                <div className="sidebar-foot">
                    <a className="user-chip" style={{ textDecoration: "none" }}>
                        <div className="avatar">{getInitials()}</div>

                        <div style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                            <b style={{ display: "block", textOverflow: "ellipsis", overflow: "hidden" }}>
                                {fullName}
                            </b>
                            <small style={{ display: "block", textOverflow: "ellipsis", overflow: "hidden", color: "#64748b" }}>
                                {email}
                            </small>
                        </div>
                    </a>
                </div>
            </aside>
        </>
    );
}