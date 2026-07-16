import React from "react";
import ProfileHeader from "./ProfileHeader";
import CompanyDetails from "./CompanyDetails";
import { useEffect } from "react";

function PageHeading() {
  return (
    <div>
      <h1 className="text-2xl font-bold text-slate-900">Company Profile</h1>
      <p className="text-sm text-slate-500 mt-1">
        Manage your company information and branding
      </p>
    </div>
  );
}

const CompanyProfile = () => {


  // if (!myCompany) {
  //   return (
  //     <div className="space-y-5">
  //       <PageHeading />
  //       <CreateCompanyForm />
  //     </div>
  //   );
  // }

  return (
    <div className="space-y-5">
      {/* Page Heading */}
      <PageHeading />
      {/* Profile Header*/}
      <ProfileHeader />

      <CompanyDetails />
    </div>
  );
};

export default CompanyProfile;
