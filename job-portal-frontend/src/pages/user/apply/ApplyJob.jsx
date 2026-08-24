import React from "react";
import { Button } from "@/components/ui/button";
import { useNavigate, useParams } from "react-router-dom";
import { ArrowLeft } from "lucide-react";
import JobInfoCard from "./JobInfoCard";

import ApplySteps from "./ApplySteps";
import { ArrowRight } from "lucide-react";
import CoverLetterEditor from "./CoverLetterEditor";
import { useState } from "react";
import { useRef } from "react";
import AdditionalDetails from "./AdditionalDetails";
import ReviewSubmission from "./ReviewSubmission";
import SelectResume from "./SelectResume";
import { useDispatch } from "react-redux";
import { submitApplication } from "../../../redux-store/application/applicationThunk";
import { useSelector } from "react-redux";
import { useEffect } from "react";
import { fetchJobById } from "../../../redux-store/job/jobThunk";
import { format } from "date-fns";

// import { job } from "../jobs/dummyjob";

const ApplyJob = () => {
  const navigate = useNavigate();
  const [currentStep, setCurrentStep] = React.useState(1);
  const [selectedResume, setSelectedResume] = React.useState(null);
  const [coverLetter, setCoverLetter] = React.useState("");
  const [expectedSalary, setExpectedSalary] = useState("");
  const [availableFrom, setAvailableFrom] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSubmitted, setIsSubmitted] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const submissionLocked = useRef(false);
  const dispatch = useDispatch();
  const { currentJob: job } = useSelector((store) => store.job);
  const { id } = useParams();

  useEffect(()=>{
    if(id){
      dispatch(fetchJobById(id))
    }
  },[id])

  const renderStep = () => {
    switch (currentStep) {
      case 1:
        return (
          <SelectResume
            selectedResume={selectedResume}
            setSelectedResume={setSelectedResume}
          />
        );
      case 2:
        return (
          <CoverLetterEditor
            coverLetter={coverLetter}
            setCoverLetter={setCoverLetter}
            selectedResume={selectedResume}
          />
        );
      case 3:
        return (
          <AdditionalDetails
            expectedSalary={expectedSalary}
            setExpectedSalary={setExpectedSalary}
            availableFrom={availableFrom}
            setAvailableFrom={setAvailableFrom}
          />
        );
      case 4:
        return (
          <ReviewSubmission
            selectedResume={selectedResume}
            coverLetter={coverLetter}
            expectedSalary={expectedSalary}
            availableFrom={availableFrom}
            job={job}
          />
        );

      default:
        return currentStep;
    }
  }

  const handleSubmit = async () => {
    if (submissionLocked.current) return;

    submissionLocked.current = true;
    setIsSubmitting(true);
    setSubmitError(null);

    try {
      const data = {
        jobId: id,
        resumeId: selectedResume,
        coverLetter: coverLetter,
        expectedSalary,
        availableFrom: availableFrom ? format(availableFrom, "yyyy-MM-dd") : null,
      };
      await dispatch(submitApplication(data)).unwrap();
      setIsSubmitted(true);
    } catch (error) {
      setSubmitError(
        typeof error === "string"
          ? error
          : error?.message || "Failed to submit application. Please try again.",
      );
      submissionLocked.current = false;
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-w-4xl max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <Button className={"py-5"} variant="ghost" onClick={() => navigate(-1)}>
        <ArrowLeft className="h-4 w-4 mr-2" />
        Back To Job
      </Button>

      <JobInfoCard job={job} />

      {isSubmitted ? (
        <div role="status" className="my-8 rounded-lg border border-green-200 bg-green-50 p-6">
          <h2 className="text-xl font-semibold text-green-900">Application submitted successfully</h2>
          <p className="mt-2 text-green-800">Your application has been received.</p>
          <Button className="mt-4" onClick={() => navigate("/applications")}>View My Applications</Button>
        </div>
      ) : (
        <>
          <ApplySteps currentStep={currentStep} />

          <div className="my-8">{renderStep()}</div>

          {submitError && (
            <p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-red-800">
              {submitError}
            </p>
          )}

          <div className="flex items-center justify-between mt-8">
            <Button
              disabled={currentStep === 1 || isSubmitting}
              variant="outline"
              onClick={() => setCurrentStep((prev) => prev - 1)}
            >
              <ArrowLeft className="h-4 w-4 mr-2" />
              Previous
            </Button>
            {currentStep < 4 ? (
              <Button
                onClick={() => setCurrentStep((prev) => prev + 1)}
                disabled={currentStep === 4}
              >
                Next
                <ArrowRight className="h-4 w-4 mr-2" />
              </Button>
            ) : (
              <Button onClick={handleSubmit} disabled={isSubmitting} aria-busy={isSubmitting}>
                {isSubmitting ? "Submitting..." : "Submit Application"}
              </Button>
            )}
          </div>
        </>
      )}
    </div>
  );
};

export default ApplyJob;
