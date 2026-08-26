import React from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "../../../components/ui/dialog";
import { Label } from "../../../components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "../../../components/ui/select";
import { Button } from "../../../components/ui/button";
import { useRef, useState } from "react";

import { useDispatch } from "react-redux";
import { updateApplicationStatus } from "../../../redux-store/application/applicationThunk";

const STATUSES = [
  { value: "PENDING", label: "Pending", color: "text-slate-600" },
  { value: "REVIEWING", label: "Reviewing", color: "text-primary" },
  { value: "SHORTLISTED", label: "Shortlisted", color: "text-indigo-600" },
  {
    value: "INTERVIEW_SCHEDULED",
    label: "Interview Scheduled",
    color: "text-violet-600",
  },
  { value: "REJECTED", label: "Rejected", color: "text-red-600" },
  { value: "HIRED", label: "Hired", color: "text-emerald-600" },
];
const UpdateStatusDialog = ({
  open,
  onClose,
  applicationId,
  currentStatus,
}) => {
  const [status, setStatus] = useState(currentStatus || "");
  const [isUpdating, setIsUpdating] = useState(false);
  const [error, setError] = useState(null);
  const submitting = useRef(false);
  const dispatch = useDispatch();

  const handleClose = () => {
    if (!submitting.current) onClose();
  };

  const handleSubmit = async () => {
    if (submitting.current || !status) return;
    submitting.current = true;
    setIsUpdating(true);
    setError(null);

    try {
      await dispatch(updateApplicationStatus({
        id: applicationId,
        status,
        note: "employer update status",
      })).unwrap();
      onClose();
    } catch (err) {
      setError(typeof err === "string" ? err : err?.message || "Failed to update status. Please try again.");
    } finally {
      submitting.current = false;
      setIsUpdating(false);
    }
  };
  
  return (
    <Dialog open={Boolean(open)} onOpenChange={(isOpen) => { if (!isOpen) handleClose(); }}>
      <DialogContent showCloseButton={!isUpdating}>
        <DialogHeader>
          <DialogTitle>Update Application Status</DialogTitle>
        </DialogHeader>

        <div className="space-y-5">
          <div className="space-y-4">
            <Label>New Status</Label>
            <Select value={status} onValueChange={setStatus} disabled={isUpdating}>
              <SelectTrigger className="text-sm w-full">
                <SelectValue placeholder="select status" />
              </SelectTrigger>
              <SelectContent>
                {STATUSES.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
          <div className="flex justify-end gap-2">
            <Button onClick={handleClose} disabled={isUpdating}>Cancel</Button>
            <Button onClick={handleSubmit} disabled={isUpdating || !status}>
              {isUpdating ? "Updating..." : "Update"}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
};

export default UpdateStatusDialog;
