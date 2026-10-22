import { http } from "../../api/apiClient";
import JSEncrypt from "jsencrypt";

export interface UserProfile {
    userID: string;
    userName?: string;
    email?: string;
    roles?: any[];
    roleIds?: string[];
    [key: string]: any;
}

// Fetch RSA Public Key via POST
export const getPublicKey = async (): Promise<string> => {
    const res = await http.post<{ publicKey: string }>("/auth/init");
    if (res.code === "000000" && res.data?.publicKey) {
        return res.data.publicKey;
    }
    throw new Error(
        typeof res.message === "string" ? res.message : "Failed to fetch public key"
    );
};

// Encrypt plain password string using RSA Public Key
export const encryptPassword = (password: string, publicKeyPem: string): string => {
    const encryptor = new JSEncrypt();
    encryptor.setPublicKey(publicKeyPem);
    const encrypted = encryptor.encrypt(password);
    if (!encrypted) {
        throw new Error("Password encryption failed");
    }
    return encrypted;
};

// Submit login credentials
export const loginApi = async (userID: string, encryptedPassword: string) => {
    return await http.post<UserProfile>("/login", {
        userID: userID,
        password: encryptedPassword,
    });
};

// Fetch current session details
export const getAuthInfo = async () => {
    return await http.post<UserProfile>("/auth/info");
};

// User Logout
export const logoutApi = async () => {
    return await http.post("/logout");
};