import UploadDecisionModal from "./UploadDecisionModal";
import type { DecisionConfig, UploadDecisionModalProps } from "./UploadDecisionModal";
import { approveUpload } from "../../../../service/admin/approval-service/approveUploadService";

const APPROVE_CONFIG: DecisionConfig = {
    id: "approve-upload",
    title: "Approve Upload",
    prompt: "Are you sure you want to approve this upload? The staged data will be published to the target table.",
    fieldLabel: "Comment",
    fieldPlaceholder: "Add a note for the audit trail",
    isRequired: false,
    confirmLabel: "Approve",
    busyLabel: "Approving...",
    confirmClassName: "btn-primary",
    successMessage: "Upload approved successfully.",
    failureMessage: "Failed to approve upload. Please try again.",
    // APPR001 — comment is optional; an empty string is sent when blank
    submit: (uploadId, comment) => approveUpload({ uploadId, comment }),
};

export default function ApproveUploadModal(props: UploadDecisionModalProps) {
    return <UploadDecisionModal {...props} config={APPROVE_CONFIG} />;
}
