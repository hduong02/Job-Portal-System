import "./App.css";
import UserLayout from "./layout/UserLayout";
import JobDetails from "./pages/user/jobs/JobDetails";
import Jobs from "./pages/user/jobs/Jobs";
import ApplyJob from "./pages/user/apply/ApplyJob";
import Profile from "./pages/user/profile/Profile";
import Application from "./pages/user/applications/Application";
import SavedJobs from "./pages/user/savedJobs/SavedJobs";
import Resumes from "./pages/user/resumes/Resumes";
import EmployerLayout from "./layout/EmployerLayout";
import Dashboard from "./pages/employer/dashboard/Dashboard";
import EmployerJobs from "./pages/employer/jobs/EmployerJobs";
import CreateJob from "./pages/employer/jobs/CreateJob";
import EmployerApplications from "./pages/employer/applications/EmployerApplications";
import AIScreening from "./pages/employer/aiScreening/AIScreening";
import CompanyProfile from "./pages/employer/companyProfile/CompanyProfile";
import AdminDashboard from "./pages/admin/dashboard/AdminDashboard";
import AdminUsers from "./pages/admin/users/AdminUsers";
import Companies from "./pages/admin/companies/Companies";
import JobMetaData from "./pages/admin/jobMetaData/JobMetaData";
import AdminProfile from "./pages/admin/settings/AdminProfile";
import AdminLayout from "./pages/admin/layout/AdminLayout";
import Login from "./pages/auth/Login";
import ResumeEdit from "./pages/user/resumeEdit/ResumeEdit";
import Register from "./pages/auth/Register";

import { Route, Routes } from "react-router-dom";
import { useDispatch, useSelector } from "react-redux";
import { useEffect } from "react";
import { fetchCurrentUser } from "./redux-store/user/userThunk";

function App() {
  const dispatch = useDispatch();
  const { isAuthenticated, user } = useSelector((state) => state.auth);

  useEffect(() => {
    const accessToken = localStorage.getItem("accessToken");
    if (accessToken) {
      dispatch(fetchCurrentUser());
    }
  }, []);
  
  return (
    <div>
      {isAuthenticated && user ? (
        <Routes>
          {/* user routes */}
          {user.role === "ROLE_JOB_SEEKER" ? (
            <Route element={<UserLayout />}>
              <Route path="/" element={<Jobs />} />

              <Route path="/jobs" element={<Jobs />} />
              <Route path="/jobs/:id" element={<JobDetails />} />
              <Route path="/apply/:id" element={<ApplyJob />} />
              <Route path="/profile" element={<Profile />} />
              <Route path="/applications" element={<Application />} />
              <Route path="/saved-jobs" element={<SavedJobs />} />
              <Route path="/resumes" element={<Resumes />} />
              <Route path="/resumes/:id/edit" element={<ResumeEdit />} />
            </Route>
          ) : user.role === "ROLE_EMPLOYER" ? (
            <Route path="/" element={<EmployerLayout />}>
              <Route path="/" element={<Dashboard />} />
              <Route path="/employer/dashboard" element={<Dashboard />} />
              <Route path="/employer/jobs" element={<EmployerJobs />} />
              <Route path="/employer/jobs/create" element={<CreateJob />} />
              <Route path="/employer/jobs/:jobId/edit" element={<CreateJob isEdit={true} />} />
              <Route path="/employer/applications" element={<EmployerApplications />} />

              <Route path="/employer/ai-screening" element={<AIScreening />} />
              <Route path="/employer/company" element={<CompanyProfile />} />
            </Route>
          ) : user.role === "ROLE_ADMIN" ? (
            <Route path="/" element={<AdminLayout />}>
              <Route path="" element={<AdminDashboard />} />

              <Route path="/admin/dashboard" element={<AdminDashboard />} />
              <Route path="/admin/users" element={<AdminUsers />} />
              <Route path="/admin/companies" element={<Companies />} />
              <Route path="/admin/job-meta" element={<JobMetaData />} />
              <Route path="/admin/settings" element={<AdminProfile />} />
            </Route>
          ) : (
            <Login />
          )}

          {/* admin routes */}
        </Routes>
      ) : (
        <Routes>
          {/* auth routes */}
          <Route path="/" element={<Login />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
        </Routes>
      )}
    </div>
  );
}

export default App;
