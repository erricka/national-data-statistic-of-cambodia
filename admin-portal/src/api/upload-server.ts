import express from "express";
import type { Request, Response } from "express";
import fileUpload from "express-fileupload";
import type { UploadedFile } from "express-fileupload";
import cors from "cors";
import path from "path";
import fs from "fs";
import { fileURLToPath } from "url";

// Reconstruct __dirname for ES Modules
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = 5000;

app.use(cors());
app.use(fileUpload());

app.post("/api/upload", (req: Request, res: Response): void => {
    if (!req.files || !req.files.file) {
        res.status(400).send("No file uploaded.");
        return;
    }

    const uploadedFile = req.files.file as UploadedFile;

    // Navigate up from src/api to project root, then into public/uploads
    const uploadDir = path.resolve(__dirname, "../../public/uploads");

    // Ensure 'public/uploads' directory exists
    if (!fs.existsSync(uploadDir)) {
        fs.mkdirSync(uploadDir, { recursive: true });
    }

    // Target file path
    const savePath = path.join(uploadDir, uploadedFile.name);

    uploadedFile.mv(savePath, (err) => {
        if (err) {
            console.error("Error moving file:", err);
            res.status(500).send(err);
            return;
        }

        console.log(`File saved directly to: public/uploads/${uploadedFile.name}`);
        res.json({ status: "success", path: `/uploads/${uploadedFile.name}` });
    });
});

app.listen(PORT, () => {
    console.log(`🚀 Local dev upload server running on http://localhost:${PORT}`);
});