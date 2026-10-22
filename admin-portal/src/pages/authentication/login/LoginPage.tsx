import { useState, type FormEvent } from "react";
import Icon from "../../../icons/Icon";
import "../../../index.css";
import { useNavigate } from "react-router-dom";
import logo from "../../../assets/logo.jpg";
import { getPublicKey, encryptPassword, loginApi } from "../../../service/auth/authService";
import { useAuth } from "../../../context/AuthContext";

const ADVANTAGE_FEATURES = [
    { icon: "shield", label: "Role-based access control (RBAC)" },
    { icon: "users", label: "Single Sign-On for government staff" }
];

export default function LoginPage() {
    const navigate = useNavigate();
    const { checkAuth } = useAuth();

    // const [currentLanguage, setCurrentLanguage] = useState("KM");
    const [userID, setUserID] = useState("");
    const [password, setPassword] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        setErrorMessage(null);
        setIsSubmitting(true);

        try {
            const publicKey = await getPublicKey();

            const encryptedPassword = encryptPassword(password, publicKey);

            const loginRes = await loginApi(userID, encryptedPassword);

            if (loginRes.code === "000000" && loginRes.data) {
                localStorage.setItem("user", JSON.stringify(loginRes.data));

                await checkAuth();
                navigate("/admin/dashboard");
            } else {
                const messageStr = typeof loginRes.message === "string"
                    ? loginRes.message
                    : loginRes.message?.messageBody || "Login failed";
                setErrorMessage(messageStr);
            }
        } catch (err: any) {
            console.error("Login process error:", err);
            setErrorMessage(err.message || "An error occurred during sign in. Please try again.");
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <div className="login">
            <div className="brand-side">
                <div>
                    <div className="top">
                        <div className="brand-mark" style={{ width: "42px", height: "42px" }}>
                            <img src={logo} alt="National Institute of Statistics Logo" />
                        </div>
                        <div>
                            <b style={{ color: "#fff", fontSize: "16px", display: "block" }}>
                                National Institute of Statistics
                            </b>
                        </div>
                    </div>
                    <h2 style={{ marginTop: "64px" }}>Secure access to the national data platform</h2>
                    <p className="lead">
                        Collect, validate, manage and publish official statistics with enterprise-grade governance and role-based access control.
                    </p>

                    <div className="feat">
                        {ADVANTAGE_FEATURES.map((feat, idx) => (
                            <div key={idx} className="r">
                                <span className="dot">
                                    <Icon name={feat.icon} />
                                </span>
                                <span>{feat.label}</span>
                            </div>
                        ))}
                    </div>
                </div>
                <small style={{ color: "#7FA3DC" }}>
                    © 2026 National Institute of Statistics · Kingdom of Cambodia
                </small>
            </div>

            <div className="form-side">
                {/* <div className="lang">
                    <button
                        className={currentLanguage === "KM" ? "on" : ""}
                        onClick={() => setCurrentLanguage("KM")}
                        type="button"
                    >
                        ខ្មែរ
                    </button>
                    <button
                        className={currentLanguage === "EN" ? "on" : ""}
                        onClick={() => setCurrentLanguage("EN")}
                        type="button"
                    >
                        EN
                    </button>
                </div> */}

                <form className="login-card" onSubmit={handleSubmit}>
                    <h1>Sign in to your account</h1>
                    <p className="sub">Enter your credentials to continue</p>

                    {errorMessage && (
                        <div style={{ padding: "10px 14px", marginBottom: "16px", backgroundColor: "#fee2e2", color: "#dc2626", borderRadius: "6px", fontSize: "14px" }}>
                            {errorMessage}
                        </div>
                    )}

                    <div className="form-row">
                        <label htmlFor="userid-input">User ID</label>
                        <div className="input-ico">
                            <Icon name="users" />
                            <input
                                id="userid-input"
                                className="input"
                                type="text"
                                placeholder="userID or officer@nis.gov.kh"
                                value={userID}
                                onChange={(e) => setUserID(e.target.value)}
                                required
                            />
                        </div>
                    </div>

                    <div className="form-row">
                        <label htmlFor="password-input">Password</label>
                        <div className="input-ico">
                            <Icon name="shield" />
                            <input
                                id="password-input"
                                className="input"
                                type="password"
                                placeholder="••••••••••••"
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                required
                            />
                        </div>
                    </div>

                    <button
                        className="btn btn-primary btn-lg btn-block"
                        type="submit"
                        disabled={isSubmitting}
                    >
                        {isSubmitting ? "Signing In..." : "Sign In"}
                    </button>

                </form>
            </div>
        </div>
    );
}