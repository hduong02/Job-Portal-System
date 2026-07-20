import React from "react";
import StateCard from "../../employer/applications/StateCard";

import { UsersIcon } from "lucide-react";
import { UserCheck } from "lucide-react";
import { Briefcase } from "lucide-react";
import { UserX } from "lucide-react";
import { useMemo } from "react";
import UserFilter from "./UserFilter";
import UserTable from "./UserTable";

import { users } from "./userData";

import { useState } from "react";

const AdminUsers = () => {
  const [roleFilter,setRoleFilter]=useState("all")
  const [statusFilter,setStatusFilter]=useState("all")

  const filtered=useMemo(()=>{

    let list=[...users]

    if(roleFilter!="all") list=list.filter((user)=>user.role===roleFilter)
    if(statusFilter!="all") list=list.filter((user)=>user.status===statusFilter)

      return list;

  },[users,roleFilter,statusFilter])

  const stats = useMemo(() => {
    const total = users.length;
    const seekers = users.filter((u) => u.role === "ROLE_JOB_SEEKER").length;
    const employers = users.filter((u) => u.role === "ROLE_EMPLOYER").length;
    const suspended = users.filter((u) => u.status === "SUSPENDED").length;
    return { total, seekers, employers, suspended };
  }, [users]);

  const summaryCards = [
    {
      label: "Total Users",
      value: stats.total,
      icon: UsersIcon,
      color: "text-primary bg-blue-50",
    },
    {
      label: "Job Seekers",
      value: stats.seekers,
      icon: UserCheck,
      color: "text-emerald-600 bg-emerald-50",
    },
    {
      label: "Employers",
      value: stats.employers,
      icon: Briefcase,
      color: "text-purple-600 bg-purple-50",
    },
    {
      label: "Suspended",
      value: stats.suspended,
      icon: UserX,
      color: "text-red-600 bg-red-50",
    },
  ];

  return (
    <div className="space-y-6">
      <section>
        <h1 className="text-2xl font-bold text-slate-900">User Management</h1>
        <p className="text-sm text-slate-500 mt-1">
          Manage and monitor all platform users
        </p>
      </section>
      <section className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {summaryCards.map((card, index) => (
          <StateCard
            key={index}
            label={card.label}
            value={card.value}
            icon={card.icon}
            color={card.color}
          />
        ))}
      </section>
      <UserFilter
        onRoleFilter={setRoleFilter}
        onStatusFilter={setStatusFilter}
      />
      <UserTable users={filtered} />
    </div>
  );
};

export default AdminUsers;
