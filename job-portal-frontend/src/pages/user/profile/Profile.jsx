import React from "react";
import ProfileHeroCard from "./ProfileHeroCard";

import PersonalInformation from "./PersonalInformation";
import AccountSecurityCard from "./AccountSecurityCard";
import ActivityCard from "./ActivityCard";
import { useDispatch, useSelector } from "react-redux";


const Profile = () => {
  const [editing, setEditing] = React.useState(false);
  const [uploading, setUploading] = React.useState(false);

  const { user } = useSelector((state) => state.auth);
  const dispatch = useDispatch();
  
  const [form, setForm] = React.useState({
    fullName: user?.fullName || "",
    phone: user?.phone || "",
    profileImage: user?.profileImage || "",
  });

  const handleSave = () => {
    dispatch(
      updateUser({
        fullName: form.fullName,
        phone: form.phone,
        profileImage: form.profileImage,
      }),
    );
  };

  const handleAvatarUpload = async (file) => {
    setUploading(true);
    try {
      const url = await uploadToCloudinary(file);
      if (!editing) {
        dispatch(
          updateUser({
            fullName: user.fullName,
            phone: user.phone,
            profileImage: url,
          }),
        );
      }
    } catch (error) {
      console.log("Error uploading avatar:", error);
    } finally {
      setUploading(false);
    }
  };

  return (
    <div>
      <div className="max-w-4xl min-w-4xl sm:px-4 px-8 py-8 space-y-6">
        <ProfileHeroCard
          user={user}
          editing={editing}
          uploading={uploading}
          onEdit={() => setEditing(true)}
          onCancel={() => setEditing(false)}
          onFileSelect={handleAvatarUpload}
          onSave={handleSave}
        />
        <PersonalInformation
          user={user}
          editing={editing}
          form={form}
          onFormChange={(patch) => setForm((f) => ({ ...f, ...patch }))}
        />
        <AccountSecurityCard user={user} />
        <ActivityCard user={user} />
      </div>
    </div>
  );
};

export default Profile;
