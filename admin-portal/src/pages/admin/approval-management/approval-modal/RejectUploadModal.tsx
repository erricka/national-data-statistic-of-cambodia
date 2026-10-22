import UploadDecisionModal from "./UploadDecisionModal";
import type { DecisionConfig, UploadDecisionModalProps } from "./UploadDecisionModal";
import { rejectUpload } from "../../../../service/admin/approval-service/rejectUploadService";

const REJECT_CONFIG: DecisionConfig = {
    id: "reject-upload",
    title: "Reject Upload",
    prompt: "Please explain why this upload is being rejected. The submitter will see this reason.",
    fieldLabel: "Reason",
    fieldPlaceholder: "e.g. Incorrect totals found in row 15. Please recheck and re-upload.",
    isRequired: true,
    confirmLabel: "Reject",
    busyLabel: "Rejecting...",
    confirmClassName: "btn-danger",
    successMessage: "Upload rejected.",
    failureMessage: "Failed to reject upload. Please try again.",
    // APPR002 — reason is mandatory (validated as reason.trim().length > 0)
    submit: (uploadId, reason) => rejectUpload({ uploadId, reason }),
};

export default function RejectUploadModal(props: UploadDecisionModalProps) {
    return <UploadDecisionModal {...props} config={REJECT_CONFIG} />;
}
