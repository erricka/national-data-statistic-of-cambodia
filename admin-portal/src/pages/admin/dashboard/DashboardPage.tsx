import Sidebar from "../../../layout/Sidebar";
import Topbar from "../../../layout/Topbar";
import "../../../index.css";
import StatisticDashboard from "../../../components/dashboard/StatisticDashboard";
import { useEffect, useState } from "react";
import { getDashboardStats } from "../../../service/admin/dashboard-service/dashboardService";

export default function DashboardPage() {

   const [stats, setStats] = useState([
        {
            iconColor: "blue",
            label: "Total Datasets",
            value: 0,
        },
        {
            iconColor: "green",
            label: "Published Datasets",
            value: 0,
        },
        {
            iconColor: "purple",
            label: "Published This Month",
            value: 0,
        },
    ]);

    useEffect(() => {
        const fetchDashboardData = async () => {
            try {
                const response = await getDashboardStats();

                if (response?.code === "000000" && response?.data) {
                    const { totalRecords, publishedThisMonth } = response.data;

                    setStats([
                        {
                            iconColor: "blue",
                            label: "Total Datasets",
                            value: totalRecords,
                        },
                        {
                            iconColor: "purple",
                            label: "Published This Month",
                            value: publishedThisMonth,
                        },
                    ]);
                }
            } catch (error) {
                console.error("DTSI010 fetch error:", error);
            }
        };

        fetchDashboardData();
    }, []);

    return (
        <div className="app">

            <Sidebar active="dashboard" />

            <div className="main">

                <Topbar pageTitle="DashBoard" />

                <main className="content">

                    {/* HEADER */}
                    <div className="page-head">
                        <div>
                            <h1>Dashboard</h1>
                            <p>វិទ្យាស្ថានជាតិស្ថិតិ-ទិន្នន័យរដ្ឋបាលក្រសួងស្ថាប័ន</p>
                        </div>

                        {/* <div className="actions">
                            <button className="btn btn-outline">Export Report</button>
                            <button className="btn btn-primary">Import Dataset</button>
                        </div> */}
                    </div>

                    {/* Top statistic grid */}
                    <StatisticDashboard items={stats} />


                    {/* <div className="grid g-3">

                        <RecentDatasetUploadDashboard recent={recent} />

                        <div className="card">
                            <div className="card-head">
                                <h3>Notifications</h3>

                                <span className="badge badge-danger">
                                    {notifs.length} new
                                </span>
                            </div>

                            <div className="card-body" style={{ padding: "10px" }}>
                                {notifs.map((n, i) => (
                                    <NotificationItemDashboard key={i} data={n} />
                                ))}
                            </div>
                        </div>

                    </div> */}

                    {/* <RecentActivitiesDashboard feed={feed} /> */}

                </main>
            </div>
        </div>
    );
}