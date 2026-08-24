import React from "react";
import { Card, CardContent } from "@/components/ui/card";
import { SparkleIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Sparkles } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Copy } from "lucide-react";
import { RotateCcw } from "lucide-react";
import { Textarea } from "@/components/ui/textarea";

import { useSelector } from "react-redux";
import { useEffect } from "react";
import { useDispatch } from "react-redux";
import { fetchResumeById } from "../../../redux-store/resume/resumeThunk";
import { generateCoverLetter } from "../../../redux-store/ai/aiThunk";

const tips = [
  "Highlight your most relevant skills and how they align with the role",
  "Mention specific achievements with measurable results",
  "Show enthusiasm for the company and why you want to join",
  "Keep it concise — 3 to 4 focused paragraphs",
];

const CoverLetterEditor = ({ coverLetter, setCoverLetter, selectedResume }) => {
  const { currentJob: job } = useSelector((store) => store.job);
  const { user } = useSelector((store) => store.auth);
  const dispatch = useDispatch();
  const [resume, setResume] = React.useState(null);
  const [resumeError, setResumeError] = React.useState(null);
  const [loadAttempt, setLoadAttempt] = React.useState(0);
  const [isGenerating, setIsGenerating] = React.useState(false);
  const [generationError, setGenerationError] = React.useState(null);
  const generationRequest = React.useRef(null);
  const resumeReady = selectedResume != null && resume?.id != null
    && String(resume.id) === String(selectedResume);

  const handleCopy = () => {
    navigator.clipboard.writeText(coverLetter);
  };

  useEffect(() => {
    let active = true;
    setResume(null);
    setResumeError(null);
    setGenerationError(null);
    setIsGenerating(false);
    generationRequest.current = null;

    if (selectedResume) {
      dispatch(fetchResumeById(selectedResume)).unwrap()
        .then((loadedResume) => {
          if (!active) return;
          if (loadedResume?.id == null || String(loadedResume.id) !== String(selectedResume)) {
            setResumeError("Could not load the selected resume. Please try again.");
            return;
          }
          setResume(loadedResume);
        })
        .catch((error) => {
          if (active) {
            setResumeError(typeof error === "string" ? error : error?.message || "Failed to load resume.");
          }
        });
    }

    return () => {
      active = false;
      generationRequest.current = null;
    };
  }, [dispatch, selectedResume, loadAttempt]);

  const handleGenerateCoverLatterWithAi = async () => {
    if (!resumeReady || generationRequest.current) return;
    const request = {};
    generationRequest.current = request;
    setIsGenerating(true);
    setGenerationError(null);

    const candidateSkills =
      resume?.skills?.map((s) => s.skillName).filter(Boolean) ?? [];

    const candidateExperience =
      resume?.workExperiences
        ?.map(
          (e) =>
            `${e.jobTitle} at ${e.companyName}${e.isCurrent ? " (current)" : ""}`,
        )
        .filter(Boolean) ?? [];

    const payload = {
      jobTitle: job?.title || "Software Engineer",
      jobDescription: job?.description || "",

      candidateName: user ? user.fullName : "",
      targetCompanyName: job?.companyId ? `Company #${job.companyId}` : "",
      candidateSummary: resume?.summary,
      candidateSkills: candidateSkills,
      candidateExperience: candidateExperience,
    };

    try {
      const result = await dispatch(generateCoverLetter(payload)).unwrap();
      if (generationRequest.current === request) {
        setCoverLetter(result.content);
      }
    } catch (error) {
      if (generationRequest.current === request) {
        setGenerationError(typeof error === "string" ? error : error?.message || "Failed to generate cover letter.");
      }
    } finally {
      if (generationRequest.current === request) {
        generationRequest.current = null;
        setIsGenerating(false);
      }
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-slate-900 mb-2">Cover Letter</h2>
        <p className="text-slate-600">
          Write a compelling cover letter or let AI help you
        </p>
      </div>

      {/* Ai Generate Card */}

      <Card className="border-blue-200 bg-linear-to-br from-blue-50 to-indigo-50">
        <CardContent className="p-6">
          <div className="flex items-start gap-4">
            <div className="h-12 w-12 rounded-lg bg-primary flex items-center justify-center shrink-0">
              <SparkleIcon className="h-6 w-6 text-white" />
            </div>

            <div className="flex-1">
              <h3 className="font-semibold text-slate-900 mb-2">
                AI-Powered Cover Letter
              </h3>
              <p className="text-sm text-slate-700 mb-4">
                {" "}
                Let our AI analyze the job description and your resume — skills,
                experience, and summary — to create a personalized cover letter
                tailored to this position.
              </p>
              <Button
                onClick={handleGenerateCoverLatterWithAi}
                className={"py-5"}
                disabled={!resumeReady || isGenerating}
                aria-busy={isGenerating}
              >
                <Sparkles className="w-4 h-4" />
                {isGenerating ? "Generating..." : "Generate with AI"}
              </Button>
              {!selectedResume ? (
                <p className="mt-2 text-sm text-slate-700">Select a resume to generate a cover letter.</p>
              ) : resumeError ? (
                <div className="mt-2">
                  <p role="alert" className="text-sm text-red-700">{resumeError}</p>
                  <Button variant="outline" className="mt-2" onClick={() => setLoadAttempt((attempt) => attempt + 1)}>
                    Retry loading resume
                  </Button>
                </div>
              ) : !resumeReady ? (
                <p role="status" className="mt-2 text-sm text-slate-700">Loading selected resume...</p>
              ) : null}
              {generationError && <p role="alert" className="mt-2 text-sm text-red-700">{generationError}</p>}
            </div>
          </div>
        </CardContent>
      </Card>

      {/* ai writing tips */}
      <Card>
        <CardContent>
          <div className="flex items-center justify-between mb-4">
            <h3 className="font-semibold text-slate-900">AI Writing Tips</h3>
            <Badge variant="secondary" className="gap-1">
              <Sparkles />
              Personalize
            </Badge>
          </div>

          <ul className="space-y-3">
            {tips.map((tip, index) => (
              <li className="flex items-start gap-3">
                <div className="h-6 w-6 rounded-full bg-blue-100 flex items-center justify-center shrink-0 mt-0.5">
                  <span className="text-xs font-semibold text-brand">
                    {index + 1}
                  </span>
                </div>
                <span>{tip}</span>
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>

      {/* Editor */}

      <Card>
        <CardContent className="p-6">
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-slate-900">
                Your Cover Letter
              </h3>
              <div className="flex gap-2">
                <Button onClick={handleCopy} variant="outline" size="sm">
                  <Copy className="h-4 w-4 mr-2" />
                  Copy
                </Button>
                <Button
                  onClick={() => setCoverLetter("")}
                  variant="outline"
                  size="sm"
                >
                  <RotateCcw />
                  Clear
                </Button>
              </div>
            </div>

            <Textarea
              placeholder="Write your cover letter here or click Generate with AI..."
              value={coverLetter}
              onChange={(e) => setCoverLetter(e.target.value)}
              className={"min-h-100 font-mono text-sm"}
            />
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export default CoverLetterEditor;
