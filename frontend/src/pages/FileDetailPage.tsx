import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { http } from '../api/http';
import { FileResponse, filesApi } from '../api/files';

export default function FileDetailPage() {
  const { id } = useParams();
  const [f, setF] = useState<FileResponse | null>(null);

  useEffect(() => {
    if (!id) return;
    http.get<FileResponse>(`/files/${id}`).then((r) => setF(r.data));
  }, [id]);

  if (!f) return <div>로딩…</div>;
  return (
    <div className="bg-white dark:bg-slate-900 rounded border border-slate-200 dark:border-slate-800 p-6">
      <Link to="/" className="text-sm text-slate-500 dark:text-slate-400">← 목록</Link>
      <h1 className="text-xl font-bold mt-2">{f.originalName}</h1>
      <dl className="mt-4 grid grid-cols-[120px_1fr] gap-y-2 text-sm">
        <dt className="text-slate-500 dark:text-slate-400">확장자</dt><dd className="uppercase">{f.extension}</dd>
        <dt className="text-slate-500 dark:text-slate-400">크기</dt><dd>{f.sizeBytes.toLocaleString()} B</dd>
        <dt className="text-slate-500 dark:text-slate-400">Content-Type</dt><dd>{f.contentType}</dd>
        <dt className="text-slate-500 dark:text-slate-400">출처</dt><dd>{f.source === 'GENERATED' ? 'Claude 생성' : '수동 업로드'}</dd>
        {f.generationJobId && (<><dt className="text-slate-500 dark:text-slate-400">Job ID</dt><dd>{f.generationJobId}</dd></>)}
        {f.description && (<><dt className="text-slate-500 dark:text-slate-400">프롬프트</dt><dd className="whitespace-pre-wrap">{f.description}</dd></>)}
        <dt className="text-slate-500 dark:text-slate-400">생성 시각</dt><dd>{new Date(f.createdAt).toLocaleString()}</dd>
      </dl>
      <div className="mt-6">
        <button onClick={() => filesApi.download(f.id, f.originalName)} className="bg-blue-600 hover:bg-blue-700 text-white rounded px-4 py-2">다운로드</button>
      </div>
    </div>
  );
}
