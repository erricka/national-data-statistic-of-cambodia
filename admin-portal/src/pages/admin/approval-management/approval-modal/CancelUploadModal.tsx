import UploadDecisionModal from "./UploadDecisionModal";
import type { DecisionConfig, UploadDecisionModalProps } from "./UploadDecisionModal";
import { cancelUpload } from "../../../../service/admin/approval-service/cancelUploadService";

const CANCEL_CONFIG: DecisionConfig = {
    id: "cancel-upload",
    title: "Cancel Upload",
    prompt: "Are you sure you want to cancel this upload? It will be withdrawn from the approval queue and cannot be approved afterwards.",
    fieldLabel: "Reason",
    fieldPlaceholder: "e.g. Uploaded incorrect file version by mistake.",
    isRequired: true,
    confirmLabel: "Cancel Upload",
    dismissLabel: "Dimiss",
    busyLabel: "Cancelling...",
    confirmClassName: "btn-danger",
    successMessage: "Upload cancelled.",
    failureMessage: "Failed to cancel upload. Please try again.",
    // APPR003
    submit: (uploadId, reason) => cancelUpload({ uploadId, reason }),
};

export default function CancelUploadModal(props: UploadDecisionModalProps) {
    return <UploadDecisionModal {...props} config={CANCEL_CONFIG} />;
}
