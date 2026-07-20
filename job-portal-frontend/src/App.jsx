import "./App.css";
import UserLayout from "./layout/UserLayout";
import JobDetails from "./pages/user/jobs/JobDetails";
import Jobs from "./pages/user/jobs/Jobs";
import ApplyJob from "./pages/user/apply/ApplyJob";
import Profile from "./pages/user/profile/Profile";
import Application from "./pages/user/applications/Application";
import SavedJobs from "./pages/user/savedJobs/SavedJobs";
import EmployerLayout from "./layout/EmployerLayout";
import Dashboard from "./pages/employer/dashboard/Dashboard";
import EmployerJobs from "./pages/employer/jobs/EmployerJobs";
import CreateJob from "./pages/employer/jobs/CreateJob";
import EmployerApplications from "./pages/employer/applications/EmployerApplications";
import AIScreening from "./pages/employer/aiScreening/AiScreening";
import CompanyProfile from "./pages/employer/companyProfile/CompanyProfile";
import AdminDashboard from "./pages/admin/dashboard/AdminDashboard";
import AdminUsers from "./pages/admin/users/AdminUsers";
import Companies from "./pages/admin/companies/Companies";
import JobMetaData from "./pages/admin/jobMetaData/JobMetaData";
import AdminProfile from "./pages/admin/settings/AdminProfile";
import AdminLayout from "./pages/admin/layout/AdminLayout";
import { Route, Routes } from "react-router-dom";

function App() {
  return (
    <div>
      <Routes>
          {/* user routes */}
          <Route element={<UserLayout />}>
            <Route path="/" element={<Jobs />} />
            <Route path="/jobs" element={<Jobs />} />
            <Route path="/jobs/:id" element={<JobDetails />} />
            <Route path="/apply/:id" element={<ApplyJob />} />
            <Route path="/profile" element={<Profile />} />
            <Route path="/applications" element={<Application />} />
            <Route path="/saved-jobs" element={<SavedJobs />} />
          </Route>

          {/* employer routes */}
          <Route path="/employer" element={<EmployerLayout />}>
              <Route path="" element={<Dashboard />} />
              <Route path="dashboard" element={<Dashboard />} />
              <Route path="applications" element={<EmployerApplications />} />
              <Route path="jobs" element={<EmployerJobs />} />
              <Route path="jobs/create" element={<CreateJob />} />
              <Route path="ai-screening" element={<AIScreening />} />
              <Route path="company" element={<CompanyProfile />} />
              {/*<Route path="/employer/jobs/:jobId/edit" element={<CreateJob isEdit={true} />} />*/}
          </Route>

          <Route path="/admin" element={<AdminLayout />}>
              <Route path="" element={<AdminDashboard />} />
              <Route path="dashboard" element={<AdminDashboard />} />
              <Route path="users" element={<AdminUsers />} />
              <Route path="companies" element={<Companies />} />
              <Route path="job-meta" element={<JobMetaData />} />
              <Route path="settings" element={<AdminProfile />} />
            </Route>
      </Routes>
    </div>
  );
}

export default App;