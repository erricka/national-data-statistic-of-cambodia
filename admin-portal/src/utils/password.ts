/**
 * Generates a random secure password strictly matching the backend 
 * UserPasswordPolicy requirements:
 * - Minimum length: 12 characters
 * - Uppercase letters: at least 2
 * - Lowercase letters: at least 3
 * - Numeric digits: at least 2
 * - Special characters: at least 1
 */
export const generateSecurePassword = (length = 12): string => {
    // Ensure total requested length is at least the policy minimum of 12
    const targetLength = Math.max(length, 12);

    const uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    const lowercase = "abcdefghijklmnopqrstuvwxyz";
    const numbers = "0123456789";
    const symbols = "!@#$%^&*()_+-=[]{}|;:,.<>?";

    const getRandomChar = (str: string) => str[Math.floor(Math.random() * str.length)];

    const chars: string[] = [
        getRandomChar(uppercase),
        getRandomChar(uppercase),

        getRandomChar(lowercase),
        getRandomChar(lowercase),
        getRandomChar(lowercase),

        getRandomChar(numbers),
        getRandomChar(numbers),

        getRandomChar(symbols),
    ];

    const allChars = uppercase + lowercase + numbers + symbols;
    while (chars.length < targetLength) {
        chars.push(getRandomChar(allChars));
    }

    for (let i = chars.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [chars[i], chars[j]] = [chars[j], chars[i]];
    }

    return chars.join("");
};

/**
 * The same UserPasswordPolicy rules as above, as a checklist for password forms.
 * The backend remains the authority and validates again on submit.
 */
export interface PasswordRule {
    id: string;
    label: string;
    test: (password: string) => boolean;
}

const countMatches = (value: string, pattern: RegExp) => (value.match(pattern) ?? []).length;

export const PASSWORD_RULES: PasswordRule[] = [
    { id: "length", label: "At least 12 characters", test: (p) => p.length >= 12 },
    { id: "upper", label: "At least 2 uppercase letters (A–Z)", test: (p) => countMatches(p, /[A-Z]/g) >= 2 },
    { id: "lower", label: "At least 3 lowercase letters (a–z)", test: (p) => countMatches(p, /[a-z]/g) >= 3 },
    { id: "digit", label: "At least 2 numbers (0–9)", test: (p) => countMatches(p, /[0-9]/g) >= 2 },
    { id: "special", label: "At least 1 special character (e.g. ! @ # $)", test: (p) => /[^A-Za-z0-9\s]/.test(p) },
];
