import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import Icon from "../icons/Icon";
import logo from "../assets/logo.jpg";

export default function NavBar() {
    const navigate = useNavigate();
    const location = useLocation(); // Get current URL path
    const [isMenuOpen, setIsMenuOpen] = useState(false);

    const toggleMenu = () => {
        setIsMenuOpen((prev) => !prev);
    };

    // Enhanced helper: checks exact match for Home, and startsWith for section sub-routes
    const isActive = (path: string) => {
        if (path === "/") {
            return location.pathname === "/";
        }
        return location.pathname.startsWith(path);
    };

    return (
        <nav className="pub-nav">
            {/* Brand Logo & Name */}
            <div
                className="brand"
                style={{ cursor: "pointer" }}
                onClick={() => navigate("/")}
            >
                <div className="brand-mark">
                    <img src={logo} alt="National Institute of Statistics Logo" />
                </div>
                <div>
                    <b>National Institute of Statistics</b>
                </div>
            </div>

            {/* Spacer pushes the menu links to the right */}
            <div style={{ flex: 1 }} />

            {/* Desktop Navigation Links (top right) */}
            <div className="pub-links">
                <Link to="/" className={isActive("/") ? "active" : ""}>
                    Home
                </Link>
                <Link to="/categories" className={isActive("/categories") ? "active" : ""}>
                    Categories
                </Link>
                <Link to="/data-explore" className={isActive("/data-explore") ? "active" : ""}>
                    Data Explore
                </Link>
                <Link to="/about" className={isActive("/about") ? "active" : ""}>
                    About
                </Link>
            </div>

            {/* Hamburger Button for Mobile */}
            <button
                className="hamburger icon-btn"
                onClick={toggleMenu}
                aria-label="Toggle Navigation"
                style={{ marginLeft: "auto" }}
            >
                <Icon name={isMenuOpen ? "close" : "menu"} />
            </button>

            {/* Mobile Dropdown Drawer */}
            {isMenuOpen && (
                <div className="pub-mobile-drawer">
                    <Link
                        to="/"
                        className={isActive("/") ? "active" : ""}
                        onClick={() => setIsMenuOpen(false)}
                    >
                        Home
                    </Link>
                    <Link
                        to="/categories"
                        className={isActive("/categories") ? "active" : ""}
                        onClick={() => setIsMenuOpen(false)}
                    >
                        Categories
                    </Link>
                    <Link
                        to="/data-explore"
                        className={isActive("/data-explore") ? "active" : ""}
                        onClick={() => setIsMenuOpen(false)}
                    >
                        Data Explore
                    </Link>
                    <Link
                        to="/about"
                        className={isActive("/about") ? "active" : ""}
                        onClick={() => setIsMenuOpen(false)}
                    >
                        About
                    </Link>

                    <div className="divider" style={{ margin: "8px 0" }} />

                    <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                        <button className="btn btn-ghost btn-sm" style={{ justifyContent: "flex-start" }}>
                            ខ្មែរ / EN
                        </button>
                        <button
                            className="btn btn-primary btn-sm"
                            style={{ width: "100%", justifyContent: "center" }}
                            onClick={() => {
                                setIsMenuOpen(false);
                                navigate("/login");
                            }}
                        >
                            <Icon name="logout" />
                            Staff Sign In
                        </button>
                    </div>
                </div>
            )}
        </nav>
    );
}