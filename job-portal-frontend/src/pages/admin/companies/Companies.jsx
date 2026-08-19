import React, { useEffect, useMemo, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import StateCard from "../../employer/applications/StateCard";
import { Building2 } from "lucide-react";
import { Clock } from "lucide-react";
import { ShieldCheck } from "lucide-react";
import { Ban } from "lucide-react";
import CompanyFilter from "./CompanyFilter";
import CompanyTable from "./CompanyTable";
import { fetchAllCompanies } from "../../../redux-store/company/companyThunk";
import PageControls from "../../../components/PageControls";

const Companies = () => {
  const dispatch = useDispatch();
  const { companies = [], companiesPage, isLoading, error } = useSelector((state) => state.company);
  const [filters, setFilters] = useState({});
  const [page, setPage] = useState(0);

  useEffect(() => {
    dispatch(fetchAllCompanies({ ...filters, page }));
  }, [dispatch, filters, page]);

  const updateFilter = (key) => (value) => {
    setPage(0);
    setFilters((current) => ({
      ...current,
      [key]: value === "all" ? undefined : value,
    }));
  };

  const stats = useMemo(() => {
    const total = companiesPage.totalElements;
    const pending = companies.filter(
      (c) => c.status === "PENDING_VERIFICATION",
    ).length;
    const active = companies.filter((c) => c.status === "ACTIVE").length;
    const suspended = companies.filter((c) => c.status === "SUSPENDED").length;
    const rejected = companies.filter((c) => c.status === "REJECTED").length;
    return { total, pending, active, suspended, rejected };
  }, [companies, companiesPage.totalElements]);

  const summaryCards = [
    {
      label: "Total Companies",
      value: stats.total,
      icon: Building2,
      color: "text-brand bg-blue-50",
    },
    {
      label: "Pending on Page",
      value: stats.pending,
      icon: Clock,
      color: "text-amber-600 bg-amber-50",
    },
    {
      label: "Active on Page",
      value: stats.active,
      icon: ShieldCheck,
      color: "text-emerald-600 bg-emerald-50",
    },
    {
      label: "Suspended on Page",
      value: stats.suspended,
      icon: Ban,
      color: "text-red-600 bg-red-50",
    },
  ];

  return (
    <div className="space-y-6">
      <section>
        <h1 className="text-2xl font-bold text-slate-900">
          Company Management
        </h1>
        <p className="text-sm text-slate-500 mt-1">
          Review, verify, and manage all registered companies
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
      <CompanyFilter
        onStatusFilter={updateFilter("status")}
        onTypeFilter={updateFilter("companyType")}
        onIndustryFilter={updateFilter("industryType")}
      />
      {error && <p className="text-sm text-red-600">{error}</p>}
      <CompanyTable companies={companies} isLoading={isLoading}/>
      <PageControls page={companiesPage} onPageChange={setPage} loading={isLoading} />
    </div>
  );
};

export default Companies;
