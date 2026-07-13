import "./App.css";
import UserLayout from "./layout/UserLayout";
import JobDetails from "./pages/user/jobs/JobDetails";
import Jobs from "./pages/user/jobs/Jobs";
import ApplyJob from "./pages/user/apply/ApplyJob";
import Profile from "./pages/user/profile/Profile";
import Application from "./pages/user/applications/Application";
import SavedJobs from "./pages/user/savedJobs/SavedJobs";
import { Route, Routes } from "react-router-dom";

function App() {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-gray-50">
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
      </Routes>
    </div>
  );
}

export default App;