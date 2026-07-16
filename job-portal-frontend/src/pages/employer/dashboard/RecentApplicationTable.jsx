import React from "react";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "../../../components/ui/card";
import { Link } from "react-router-dom";
import { Button } from "../../../components/ui/button";
import { ArrowRight } from "lucide-react";
import ApplicationTable from "../applications/ApplicationTable";

import { recent as applications } from "./recentApplications"

const RecentApplicationTable = () => {
  const recent = [...applications]
    .sort((a, b) => new Date(b.appliedAt) - new Date(a.appliedAt))
    .slice(0, 5);

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between py-2">
        <CardTitle>Recent Applications</CardTitle>
        <Link>
          <Button variant="ghost">
            View All <ArrowRight />
          </Button>
        </Link>
      </CardHeader>
      <CardContent className={"px-0"}>
        <ApplicationTable applications={recent} isFullMode={false} />
      </CardContent>
    </Card>
  );
};

export default RecentApplicationTable;
