import { BookMarked } from "lucide-react";
import React from "react";
import { Briefcase } from "lucide-react";

import SavedJobCard from "./SavedJobCard";
import { Button } from "../../../components/ui/button";

import { useDispatch } from "react-redux";
import { useEffect } from "react";
import { fetchMySavedJobs } from "../../../redux-store/saveJobs/saveJobThunk";
import { useSelector } from "react-redux";
import { useState } from "react";
import PageControls from "../../../components/PageControls";

// import { savedJobs } from "./dummySavedJobs";

const SavedJobs = () => {
  const dispatch=useDispatch()
  const {savedJobs, savedJobsPage, isLoading, error}=useSelector(store=>store.savedJob)
  const [page, setPage] = useState(0);

  useEffect(()=>{
    dispatch(fetchMySavedJobs({ page }))
  },[dispatch, page])
  
  return (
    <div className="max-w-5xl min-w-5xl max-auto px-4 sm:px-6 lg:px-8 py-8">
      <div className="flex justify-between items-center">
        <div className="mb-8">
          <h1 className="text-2xl font-bold text-slate-900 flex items-center gap-2">
            <BookMarked className="h-6 w-6 text-primary" />
            Saved Jobs
          </h1>
          {savedJobsPage.totalElements>0 && <p className="text-slate-500 text-sm mt-1">{savedJobsPage.totalElements} Jobs Saved</p>}
        </div>
        <div>
          <Button variant="outline" className={"py-5"}>
            <Briefcase />
            Browse Jobs
          </Button>
        </div>
      </div>

      {/* job list */}

      <div className="space-y-4">
        {savedJobs.map((job)=><SavedJobCard key={job.id} savedJob={job}/>)}
        {error && <p className="text-sm text-red-600">{error}</p>}
        {!isLoading && !error && savedJobs.length === 0 && <p className="text-sm text-slate-500">No saved jobs.</p>}
      </div>
      <PageControls page={savedJobsPage} onPageChange={setPage} loading={isLoading} />
    </div>
  );
};

export default SavedJobs;
