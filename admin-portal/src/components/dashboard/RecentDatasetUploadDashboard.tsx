type Props = {
    recent: any[];
};

export default function RecentDatasetUploadDashboard({ recent }: Props) {
    return (
        <div className="card col-2">

            {/* HEADER */}
            <div className="card-head">
                <h3>Recent Dataset Uploads</h3>

                <a className="btn btn-ghost btn-sm" href="datasets.html">
                    View all
                </a>
            </div>

            {/* TABLE */}
            <div className="table-wrap">
                <table className="tbl">
                    <thead>
                        <tr>
                            <th>Dataset</th>
                            <th>Department</th>
                            <th>Status</th>
                            <th>Date</th>
                        </tr>
                    </thead>

                    <tbody>
                        {recent.map((r, i) => (
                            <tr key={i}>
                                <td>{r[0]}</td>
                                <td>{r[1]}</td>
                                <td>{r[2]}</td>
                                <td>{r[3]}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

        </div>
    );
}