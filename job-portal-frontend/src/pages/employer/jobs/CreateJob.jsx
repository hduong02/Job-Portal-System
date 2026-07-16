import React from "react";
import JobSection from "./JobSection";
import { Briefcase } from "lucide-react";
import { Input } from "../../../components/ui/input";
import JobField from "./JobField";
import { useState } from "react";
import { Textarea } from "../../../components/ui/textarea";
import AiButton from "./AiButton";
import { Layers } from "lucide-react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "../../../components/ui/select";

import { Tag } from "lucide-react";
import MultiSelect from "./MultiSelect";

import { MapPin } from "lucide-react";
import { DollarSign } from "lucide-react";
import { Settings } from "lucide-react";
import { Button } from "../../../components/ui/button";
import { Send } from "lucide-react";
import { Save } from "lucide-react";
import { Separator } from "../../../components/ui/separator";
import { Badge } from "../../../components/ui/badge";

import { useParams } from "react-router-dom";

import { categories } from "./categories"
import { tags } from "./tags";
import { skills } from "./skills";

const EXP_LEVELS = [
  "ENTRY_LEVEL",
  "JUNIOR",
  "MID_LEVEL",
  "SENIOR_LEVEL",
  "LEAD",
  "EXECUTIVE",
];
const JOB_TYPES = [
  "FULL_TIME",
  "PART_TIME",
  "CONTRACT",
  "INTERNSHIP",
  "FREELANCE",
  "REMOTE",
];
const WORK_MODES = ["REMOTE", "HYBRID", "ON_SITE"];
const SALARY_PERIODS = ["HOURLY", "DAILY", "WEEKLY", "MONTHLY", "YEARLY"];
const CURRENCIES = ["USD", "INR", "EUR", "GBP", "CAD", "AUD", "SGD"];

const CreateJob = ({ isEdit = false }) => {
  const { jobId } = useParams();

  const [form, setForm] = useState({
    title: "",
    description: "",
    requirements: "",
    responsibilities: "",
    benefits: "",
    skillIds: [],
    tagIds: [],
    categoryId: null,
    experienceLevel: null,
    jobType: null,
    workMode: null,
    openings: 1,
    city: "",
    state: "",
    country: "",
    zipCode: "",
    minSalary: "",
    maxSalary: "",
    currency: "",
    salaryPeriod: "",
    applicationDeadline: "",
    expiresAt: "",
  });

  const set = (f) => (e) => setForm((p) => ({ ...p, [f]: e.target.value }));
  const setVal = (f) => (v) => setForm((prev) => ({ ...prev, [f]: v }));

  const checklistItems = [
    ["Title", !!form.title.trim()],
    ["Description", !!form.description.trim()],
    ["Category", !!form.categoryId],
    ["Job Type", !!form.jobType],
    ["Work Mode", !!form.workMode],
    ["Experience Level", !!form.experienceLevel],
    ["Skills", form.skillIds?.length > 0],
    ["Location", !!(form.city || form.country)],
    ["Salary", !!(form.minSalary || form.maxSalary)],
  ];

let isGeneratingJobDescription = false;
let isGeneratingJobRequirements = false;
let isSuggestingSalary = false;
let isRecommendingSkills = false;
let isGeneratingJobResponsibilities = false;
let isGeneratingJobBenefits = false;
let isRecommendingTags = false;

  const handleSubmit = () => {
    
  };

  const selectedSkillsName = (skillOpts) => {
    skillOpts
      .filter((s) => form.skillIds.includes(s.id))
      .map((s) => s.name)
      .join(", ");
  };


  const handleGenerateDescription = () => {

  };

  const handleAutoFillRequirements = () => {

  };

  const handleAutoFillResponsibilities = () => {

  };

  const handleAutoFillBenefits = () => {

  };

  const handleRecommendTags = () => {

  };

  const handleRecommendSkills = () => {

  };

  const handleSuggestSalary = () => {

  };


  return (
    <div className="space-y-5">
      <section>
        <h1 className="text-2xl font-bold text-slate-900">Post a New Jobs</h1>
        <p className="text-sm text-slate-500 mt-1">
          Fill in the details below. You can save a draft and publish later.
        </p>
      </section>

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
        {/* left section */}
        <div className="xl:col-span-2 space-y-5">
          {/* Job Details */}
          <JobSection icon={Briefcase} title={"Job Details"}>
            <JobField label={"Job Title"} required>
              <Input
                value={form.title}
                placeholder="e.g. Senior React Developer"
                className="border-slate-200"
                onChange={set("title")}
              />
            </JobField>

            <JobField
              label={"Job Description"}
              required
              action={
                <AiButton
                  label={"Generate with AI"}
                  disabled={!form.title.trim()}
                  onClick={handleGenerateDescription}
                  isLoading={isGeneratingJobDescription}
                />
              }
            >
              <Textarea
                value={form.description}
                placeholder="Describe the role, culture, and what makes this position exciting..."
                className="border-slate-200 resize-none text-sm"
                onChange={set("description")}
              />
            </JobField>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <JobField
                label={"Requirements"}
                required
                action={
                  <AiButton
                    label={"Auto-fill from title"}
                    disabled={!form.title.trim()}
                    onClick={handleAutoFillRequirements}
                    isLoading={isGeneratingJobRequirements}
                  />
                }
              >
                <Textarea
                  value={form.requirements}
                  placeholder="List the skills, experience, and qualifications needed for this role..."
                  className="border-slate-200 resize-none text-sm"
                  onChange={set("requirements")}
                />
              </JobField>

              <JobField
                label={"Responsibilities"}
                required
                action={
                  <AiButton
                    label={"Auto-fill from title"}
                    disabled={!form.title.trim()}
                    onClick={handleAutoFillResponsibilities}
                    isLoading={isGeneratingJobResponsibilities}
                  />
                }
              >
                <Textarea
                  value={form.responsibilities}
                  placeholder="List the key responsibilities and duties for this role..."
                  className="border-slate-200 resize-none text-sm"
                  onChange={set("responsibilities")}
                />
              </JobField>
            </div>
            <JobField
              label={"Benefits"}
              required
              action={
                <AiButton
                  label={"Auto-fill from title"}
                  disabled={!form.title.trim()}
                  onClick={handleAutoFillBenefits}
                  isLoading={isGeneratingJobBenefits}
                />
              }
            >
              <Textarea
                value={form.benefits}
                placeholder="List the benefits and perks for this role..."
                className="border-slate-200 resize-none text-sm"
                onChange={set("benefits")}
              />
            </JobField>
          </JobSection>

          {/* Classification */}
          <JobSection icon={Layers} title={"Classification"}>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <JobField label={"Category"} required>
                <Select
                  value={form.categoryId}
                  onValueChange={setVal("categoryId")}
                >
                  <SelectTrigger className="border-slate-200 text-sm w-full">
                    <SelectValue placeholder="Select a category" />
                  </SelectTrigger>
                  <SelectContent>
                    {categories.map((category) => (
                      <SelectItem key={category.id} value={category.id}>
                        {category.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </JobField>
              <JobField label={"Experience Level"} required>
                <Select
                  value={form.experienceLevel}
                  onValueChange={setVal("experienceLevel")}
                >
                  <SelectTrigger className="border-slate-200 text-sm w-full">
                    <SelectValue placeholder="Select an experience level" />
                  </SelectTrigger>
                  <SelectContent>
                    {EXP_LEVELS.map((level) => (
                      <SelectItem key={level} value={level}>
                        {level.replace("_", " ")}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </JobField>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <JobField label={"Job Type"} required>
                <Select value={form.jobType} onValueChange={setVal("jobType")}>
                  <SelectTrigger className="border-slate-200 text-sm w-full">
                    <SelectValue placeholder="Select a job type" />
                  </SelectTrigger>
                  <SelectContent>
                    {JOB_TYPES.map((job) => (
                      <SelectItem key={job} value={job}>
                        {job.replace("_", " ")}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </JobField>
              <JobField label={"Work Mode"} required>
                <Select
                  value={form.workMode}
                  onValueChange={setVal("workMode")}
                >
                  <SelectTrigger className="border-slate-200 text-sm w-full">
                    <SelectValue placeholder="Select a work mode" />
                  </SelectTrigger>
                  <SelectContent>
                    {WORK_MODES.map((mode) => (
                      <SelectItem key={mode} value={mode}>
                        {mode.replace("_", " ")}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </JobField>
            </div>

            <JobField label={"Number Of Openings"} required>
              <Input
                value={form.openings}
                placeholder="e.g. 50"
                type={"number"}
                className="border-slate-200"
                onChange={set("openings")}
              />
            </JobField>
          </JobSection>

          {/* Skills and Tags */}
          <JobSection icon={Tag} title={"Skills & Tags"}>
            <JobField
              hint="Select skills from the library — candidates will be matched against these"
              label={"Skills"}
              required
              action={
                <AiButton
                  label={"Generate with AI"}
                  disabled={!form.title.trim()}
                  onClick={handleRecommendSkills}
                  isLoading={isRecommendingSkills}
                />
              }
            >
              <MultiSelect
                options={skills}
                selectedIds={form.skillIds}
                onChange={setVal("skillIds")}
                placeholder="Select Skills..."
              />
            </JobField>

            <JobField
              label={"Tags"}
              hint="Keywords that improve job discoverability"
              required
              action={
                <AiButton
                  label={"Generate with AI"}
                  disabled={!form.title.trim()}
                  onClick={handleRecommendTags}
                  isLoading={isRecommendingTags}
                />
              }
            >
              <MultiSelect
                options={tags}
                selectedIds={form.tagIds}
                onChange={setVal("tagIds")}
                placeholder="Select Tags..."
              />
            </JobField>
          </JobSection>

          <JobSection icon={MapPin} title={"Location"}>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <JobField label={"City"}>
                <Input
                  value={form.city}
                  placeholder="e.g. New York"
                  className="border-slate-200"
                  onChange={set("city")}
                />
              </JobField>

              <JobField label={"State / Province"} required>
                <Input
                  value={form.state}
                  placeholder="e.g. New York"
                  className="border-slate-200"
                  onChange={set("state")}
                />
              </JobField>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <JobField label={"Country"}>
                <Input
                  value={form.country}
                  placeholder="e.g. USA"
                  className="border-slate-200"
                  onChange={set("country")}
                />
              </JobField>

              <JobField label={"Zip Code"} required>
                <Input
                  value={form.zipCode}
                  placeholder="e.g. 10001"
                  className="border-slate-200"
                  onChange={set("zipCode")}
                />
              </JobField>
            </div>
            <JobField label={"Full Address"} required>
              <Input
                value={form.address}
                placeholder="e.g. 123 Main St, New York, NY 10001, USA"
                className="border-slate-200"
                onChange={set("address")}
              />
            </JobField>
          </JobSection>

          <JobSection icon={DollarSign} title={"Salary & Compensation"}>
            <div className="flex items-center justify-between rounded-lg bg-violet-50 border border-violet-100 px-3 py-2">
              <p className="text-xs">
                AI can estimate a competitive salary range for this role.
              </p>
              <AiButton
                label={"Suggest Salary"}
                disabled={!form.title.trim()}
                onClick={handleSuggestSalary}
                isLoading={isSuggestingSalary}
              />
            </div>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <JobField label={"Min Salary"}>
                <Input
                  type={"number"}
                  value={form.minSalary}
                  placeholder="e.g. 50000"
                  className="border-slate-200"
                  onChange={set("minSalary")}
                  min={0}
                />
              </JobField>

              <JobField label={"Max Salary"} required>
                <Input
                  type={"number"}
                  value={form.maxSalary}
                  placeholder="e.g. 100000"
                  className="border-slate-200"
                  onChange={set("maxSalary")}
                  min={form.minSalary || 0}
                />
              </JobField>

              <JobField label={"Currrency"} required>
                <Select
                  value={form.currency}
                  onValueChange={setVal("currency")}
                >
                  <SelectTrigger className="border-slate-200 text-sm w-full">
                    <SelectValue placeholder="Select a category" />
                  </SelectTrigger>
                  <SelectContent>
                    {CURRENCIES.map((c) => (
                      <SelectItem key={c} value={c}>
                        {c}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </JobField>
            </div>

            <JobField label={"Salary Period"} required>
              <Select
                value={form.salaryPeriod}
                onValueChange={setVal("salaryPeriod")}
              >
                <SelectTrigger className="border-slate-200 text-sm w-full">
                  <SelectValue placeholder="Select a category" />
                </SelectTrigger>
                <SelectContent>
                  {SALARY_PERIODS.map((period) => (
                    <SelectItem key={period} value={period}>
                      {period}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </JobField>
          </JobSection>

          {/* Posting & Settings */}
          <JobSection icon={Settings} title={"Posting & Settings"}>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <JobField label="Application Deadline" hint="Last date to apply">
                <Input
                  type={"date"}
                  value={form.applicationDeadline}
                  className="border-slate-200"
                  onChange={set("applicationDeadline")}
                />
              </JobField>

              <JobField
                label="Posting Expires At"
                hint="Job auto-expires on this date"
                required
              >
                <Input
                  type={"date"}
                  value={form.expiresAt}
                  className="border-slate-200"
                  onChange={set("expiresAt")}
                />
              </JobField>
            </div>
          </JobSection>
        </div>
        <div className="space-y-4">
          <div className="sticky top-6 space-y-4">
            <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm space-y-3">
              <h3>{isEdit ? "Save Changes" : "Publish Job"}</h3>
              <Button onClick={handleSubmit} className="w-full gap-2 ">
                <Send className="w-4 h-4" />
                {isEdit ? "Save Changes" : "Publish Job"}
              </Button>
              <Button
                variant="outline"
                className="w-full gap-2 border-slate-200"
              >
                <Save className="h-4 w-4" />
                {isEdit ? "Save as Draft" : "Save as Draft"}
              </Button>

              <Separator />

              <div className="space-y-1.5 text-xs text-slate-500">
                <div className="flex justify-between">
                  <span>Status after save:</span>
                  <Badge
                    variant="outline"
                    className="text-xs bg-amber-50 text-amber-700 border-amber-200"
                  >
                    Draft
                  </Badge>
                </div>
                <div className="flex justify-between">
                  <span>Status after publish:</span>
                  <Badge
                    variant="outline"
                    className="text-xs bg-emerald-50 text-emerald-700 border-emerald-200"
                  >
                    Open
                  </Badge>
                </div>
              </div>
            </div>

            <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm space-y-3">
              <h3 className="text-sm font-semibold text-slate-800 mb-3">
                Completion Checklist
              </h3>

              <ul className="space-y-2">
                {checklistItems.map(([label, completed]) => (
                  <li className="flex items-center gap-2 text-xs">
                    <span
                      className={
                        completed ? "text-emerald-500" : "text-slate-300"
                      }
                    >
                      {completed ? "✓" : "○"} {label}
                    </span>
                    <span>{label}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default CreateJob;
