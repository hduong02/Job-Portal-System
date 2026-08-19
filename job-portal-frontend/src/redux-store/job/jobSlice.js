import { createSlice } from "@reduxjs/toolkit";
import {
  closeJob,
  createJob,
  deleteJob,
  fetchAllJobsAdmin,
  fetchJobById,
  fetchJobs,
  fetchMyJobs,
  publishJob,
  updateJob,
} from "./jobThunk";
import { replaceInList } from "../utils/replaceInList";
import { emptyPage, pageResult } from "../utils/pageResult";

const initialState = {
  jobs: [],
  jobsPage: emptyPage,
  jobLoading: false,
  jobError: null,
  adminJobs: [],
  adminJobsPage: emptyPage,
  adminJobLoading: false,
  myJobs: [],
  myJobsPage: emptyPage,
  currentJob: null,
  isLoading: false,
  error: null,
  actionError: null,
  isActionLoading: false,
};

const jobSlice = createSlice({
  name: "job",
  initialState,
  extraReducers: (builder) => {
    builder
      .addCase(fetchJobs.pending, (state) => {
        state.jobLoading = true;
        state.jobError = null;
      })
      .addCase(fetchJobs.fulfilled, (state, action) => {
        state.jobLoading = false;
        const result = pageResult(action.payload);
        state.jobs = result.content;
        state.jobsPage = result.page;
        state.jobError = null;
      })
      .addCase(fetchJobs.rejected, (state, action) => {
        state.jobLoading = false;
        state.jobError = action.payload;
      });

    // ── fetchAllJobsAdmin ─────────────────────────────────────────────────────
    builder
      .addCase(fetchAllJobsAdmin.pending, (s) => {
        s.adminJobLoading = true;
        s.jobError = null;
      })
      .addCase(fetchAllJobsAdmin.fulfilled, (s, { payload }) => {
        s.adminJobLoading = false;
        const result = pageResult(payload);
        s.adminJobs = result.content;
        s.adminJobsPage = result.page;
        s.jobError = null;
      })
      .addCase(fetchAllJobsAdmin.rejected, (s, { payload }) => {
        s.adminJobLoading = false;
        s.jobError = payload;
      });

    // ── fetchMyJobs ───────────────────────────────────────────────────────────
    builder
      .addCase(fetchMyJobs.pending, (s) => {
        s.isLoading = true;
        s.error = null;
      })
      .addCase(fetchMyJobs.fulfilled, (s, { payload }) => {
        s.isLoading = false;
        const result = pageResult(payload);
        s.myJobs = result.content;
        s.myJobsPage = result.page;
        s.jobError = null;
      })
      .addCase(fetchMyJobs.rejected, (s, { payload }) => {
        s.isLoading = false;
        s.jobError = payload;
      });

    // ── fetchJobById ──────────────────────────────────────────────────────────
    builder
      .addCase(fetchJobById.pending, (s) => {
        s.isLoading = true;
        s.error = null;
      })
      .addCase(fetchJobById.fulfilled, (s, { payload }) => {
        s.isLoading = false;
        s.currentJob = payload;
      })
      .addCase(fetchJobById.rejected, (s, { payload }) => {
        s.isLoading = false;
        s.error = payload;
      });

    // ── createJob ─────────────────────────────────────────────────────────────
    builder
      .addCase(createJob.pending, (s) => {
        s.isActionLoading = true;
        s.actionError = null;
      })
      .addCase(createJob.fulfilled, (s, { payload }) => {
        s.isActionLoading = false;
        s.myJobs.unshift(payload);
        s.currentJob = payload;
      })
      .addCase(createJob.rejected, (s, { payload }) => {
        s.isActionLoading = false;
        s.actionError = payload;
      });

    // ── updateJob ─────────────────────────────────────────────────────────────
    builder
      .addCase(updateJob.pending, (s) => {
        s.isActionLoading = true;
        s.actionError = null;
      })
      .addCase(updateJob.fulfilled, (s, { payload }) => {
        s.isActionLoading = false;
        s.currentJob = payload;
        replaceInList(s.myJobs, payload);
      })
      .addCase(updateJob.rejected, (s, { payload }) => {
        s.isActionLoading = false;
        s.actionError = payload;
      });

    // ── publishJob ────────────────────────────────────────────────────────────
    builder
      .addCase(publishJob.pending, (s) => {
        s.isActionLoading = true;
        s.actionError = null;
      })
      .addCase(publishJob.fulfilled, (s, { payload }) => {
        s.isActionLoading = false;
        replaceInList(s.myJobs, payload);
        if (s.currentJob?.id === payload.id) s.currentJob = payload;
      })
      .addCase(publishJob.rejected, (s, { payload }) => {
        s.isActionLoading = false;
        s.actionError = payload;
      });

    // ── closeJob ──────────────────────────────────────────────────────────────
    builder
      .addCase(closeJob.pending, (s) => {
        s.isActionLoading = true;
        s.actionError = null;
      })
      .addCase(closeJob.fulfilled, (s, { payload }) => {
        s.isActionLoading = false;
        replaceInList(s.myJobs, payload);
        if (s.currentJob?.id === payload.id) s.currentJob = payload;
      })
      .addCase(closeJob.rejected, (s, { payload }) => {
        s.isActionLoading = false;
        s.actionError = payload;
      });

    // ── deleteJob ─────────────────────────────────────────────────────────────
    builder
      .addCase(deleteJob.pending, (s) => {
        s.isActionLoading = true;
        s.actionError = null;
      })
      .addCase(deleteJob.fulfilled, (s, { payload: deletedId }) => {
        s.isActionLoading = false;
        s.myJobs = s.myJobs.filter((j) => j.id !== deletedId);
        if (s.currentJob?.id === deletedId) s.currentJob = null;
      })
      .addCase(deleteJob.rejected, (s, { payload }) => {
        s.isActionLoading = false;
        s.actionError = payload;
      });
  },
});


export default jobSlice.reducer;
