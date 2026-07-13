import React from "react";
import { Button } from "@/components/ui/button";
import { useNavigate, useParams } from "react-router-dom";
import { ArrowLeft } from "lucide-react";
import JobInfoCard from "./JobInfoCard";

import ApplySteps from "./ApplySteps";
import { ArrowRight } from "lucide-react";
import CoverLetterEditor from "./CoverLetterEditor";
import { useState } from "react";
import AdditionalDetails from "./AdditionalDetails";
import ReviewSubmission from "./ReviewSubmission";
import SelectResume from "./SelectResume";

import { job } from "../jobs/dummyjob";

const ApplyJob = () => {
  const navigate = useNavigate();
  const [currentStep, setCurrentStep] = React.useState(1);
  const [selectedResume, setSelectedResume] = React.useState(null);
  const [coverLetter, setCoverLetter] = React.useState("");
  const [expectedSalary, setExpectedSalary] = useState("");
  const [availableFrom, setAvailableFrom] = useState(null);
  const handleSubmit = () => {
    
  }
  // const dispatch = useDispatch();
  // const { currentJob: job } = useSelector((store) => store.job);
  // const { id } = useParams();

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

  return (
    <div className="min-w-4xl max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <Button className={"py-5"} variant="ghost" onClick={() => navigate(-1)}>
        <ArrowLeft className="h-4 w-4 mr-2" />
        Back To Job
      </Button>

      <JobInfoCard job={job} />

      <ApplySteps currentStep={currentStep} />

      <div className="my-8">{renderStep()}</div>

      <div className="flex items-center justify-between mt-8">
        <Button
          disabled={currentStep === 1}
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
          <Button onClick={handleSubmit}>Submit Application</Button>
        )}
      </div>
    </div>
  );
};

export default ApplyJob;
