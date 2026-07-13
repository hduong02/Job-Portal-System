import { Wand2 } from "lucide-react";
import { Sparkles } from "lucide-react";
import React from "react";
import { Button } from "../../../components/ui/button";
import { Briefcase } from "lucide-react";

import { X } from "lucide-react";
import { Badge } from "../../../components/ui/badge";
import { TrendingUp } from "lucide-react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "../../../components/ui/select";
import { useState } from "react";
import JobFilter from "./JobFilter";

import JobCard from "./JobCard";
import { jobs } from './dummyjobs';

const SORT_OPTIONS = [
  { value: "newest", label: "Newest first" },
  { value: "salary-high", label: "Salary: high → low" },
  { value: "salary-low", label: "Salary: low → high" },
];

const Jobs = () => {
  const [aiQuery, setAiQuery] = React.useState("");
  const [sortBy, setSortBy] = useState(SORT_OPTIONS[0].value);
  const [page, setPage] = useState(1);

  const handleSortBy = (value) => {
    (setSortBy(value), setPage(1));
  };

  const handleEnhance = () => {

  }

  return (

    <div className="w-full">
      {/* Hero section */}

      <section className="bg-linear-to-br from-primary via-blue-950 to-indigo-950 py-12 px-4 flex flex-col items-center justify-center">
        <div className="min-w-4xl max-w-4xl max-auto text-center">
          <div className="inline-flex items-center gap-2 text-white bg-white/15 rounded-full mb-4 text-sm py-1.5 px-3 backdrop-blur-sm">
            <Sparkles className="h-4 w-4" />
            AI-Powered Job Search
          </div>
          <h1 className="text-3xl sm:text-4xl font-bold text-white mb-2">
            Find Your Next Opportunity
          </h1>
          <p className="text-blue-100 mb-8 text-sm sm:text-base">
            Discover the perfect job match with our AI-powered search engine.
          </p>

          {/* Ai Search Card */}

          <div className="bg-white rounded-2xl shadow-xl p-4 space-y-3">
            <div className="relative">
              <Wand2 className="absolute left-3 top-3 h-4 w-4 text-slate-400 pointer-events-none" />
              <textarea
                value={aiQuery}
                onChange={(e) => setAiQuery(e.target.value)}
                placeholder={
                  "Describe the job you're looking for... \nE.g. Software Engineer with 5 years experience in React and Spring boot"
                }
                className="w-full resize-none rounded-lg border border-slate-200 bg-slate-50 pl-9 pr-4 py-2.5 text-sm text-slate-800 placeholder:text-slate-400 focus:outline-none transition"
              />
            </div>
            <div className="flex items-center justify-between">
              <p className="text-xs text-slate-400">
                Tip: Ctrl + Enter to search
              </p>
              <Button
                // onClick={handleEnhance}
                className=" rounded-xl px-6 py-6 cursor-pointer"
              >
                <Wand2 className="h-4 w-4 mr-1.5" /> Search With AI
              </Button>
            </div>
          </div>
        </div>
      </section>

      <main className="flex flex-col items-center">
        <section className="min-w-7xl max-w-7xl max-auto px-4 sm:px-6 lg:px-8 py-8">
          <div className="flex flex-row items-center justify-between gap-3 mb-6">
            <div className="flex items-center gap-3">
              <div className="flex items-center gap-3">
                <div className="h-8 w-8 rounded-lg bg-blue-100 flex items-center justify-center">
                  <Briefcase className="h-4 w-4 text-brand" />
                </div>
                <div>
                  <p className="text-sm font-semibold text-slate-800">
                    {jobs.length} Jobs Found
                  </p>
                  <p className="text-xs text-slate-500">java developer</p>
                </div>
              </div>
              <Badge className="bg-blue-100 text-primary hover:bg-blue-200 cursor-pointer">
                <X className="h-3 w-3 mr-1" />
                {4} filters
              </Badge>

            </div>
            <div className="">
              <div className="flex items-center gap-1.5">
                <TrendingUp />
                <Select value={sortBy} onValueChange={handleSortBy}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {SORT_OPTIONS.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>
          {/* main grid */}
          <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
            <div className="sticky top-6 h-[calc(100vh-3rem)] overflow-y-auto scrollbar-hide border rounded-xl shadow-sm">
              <JobFilter />
            </div>

            {/* Job list */}
            <div className="lg:col-span-3 space-y-5 ">
              {jobs.map((job) => (
                <JobCard key={job.id} job={job} />
              ))}
            </div>
          </div>
        </section>
      </main>

    </div>
  )
}

export default Jobs;