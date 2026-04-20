import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { filesApi, FileResponse, FileSource } from '../api/files';

const PAGE_SIZE = 20;

export default function FileListPage() {
  const [rows, setRows] = useState<FileResponse[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [source, setSource] = useState<FileSource | ''>('');
  const [keyword, setKeyword] = useState('');

  const load = async (p = 0) => {
    const res = await filesApi.list({
      page: p,
      size: PAGE_SIZE,
      source: source || undefined,
      keyword: keyword || undefined
    });
    setRows(res.content);
    setPage(res.number);
    setTotalPages(Math.max(res.totalPages, 1));
  };

  useEffect(() => { load(0); }, [source]);

  const onDelete = async (id: number) => {
    if (!confirm('삭제할까요?')) return;
    await filesApi.remove(id);
    load(page);
  };

  const fmtSize = (b: number) => {
    if (b < 1024) return `${b}B`;
    if (b < 1024 * 1024) return `${(b / 1024).toFixed(1)}KB`;
    return `${(b / 1024 / 1024).toFixed(1)}MB`;
  };

  return (
    <div>
      <div className="flex flex-wrap gap-2 mb-4 items-center">
        <h1 className="text-xl font-bold mr-auto">파일 목록</h1>
        <input className="border rounded px-3 py-1" placeholder="이름/설명 검색" value={keyword} onChange={(e) => setKeyword(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && load(0)} />
        <select className="border rounded px-2 py-1" value={source} onChange={(e) => setSource(e.target.value as FileSource | '')}>
          <option value="">전체</option>
          <option value="MANUAL">수동 업로드</option>
          <option value="GENERATED">Claude 생성</option>
        </select>
        <Link to="/upload" className="bg-slate-800 text-white rounded px-3 py-1">업로드</Link>
        <Link to="/generate" className="bg-blue-600 text-white rounded px-3 py-1">문서 생성</Link>
      </div>

      <div className="bg-white rounded shadow-sm overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-slate-100">
            <tr>
              <th className="text-left px-3 py-2">이름</th>
              <th className="text-left px-3 py-2">확장자</th>
              <th className="text-left px-3 py-2">크기</th>
              <th className="text-left px-3 py-2">출처</th>
              <th className="text-left px-3 py-2">업로드 시각</th>
              <th className="px-3 py-2"></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((f) => (
              <tr key={f.id} className="border-t hover:bg-slate-50">
                <td className="px-3 py-2"><Link to={`/files/${f.id}`} className="text-slate-800 hover:underline">{f.originalName}</Link></td>
                <td className="px-3 py-2 uppercase">{f.extension}</td>
                <td className="px-3 py-2">{fmtSize(f.sizeBytes)}</td>
                <td className="px-3 py-2">
                  <span className={`text-xs px-2 py-0.5 rounded ${f.source === 'GENERATED' ? 'bg-blue-100 text-blue-700' : 'bg-slate-200 text-slate-700'}`}>
                    {f.source === 'GENERATED' ? 'Claude' : '수동'}
                  </span>
                </td>
                <td className="px-3 py-2">{new Date(f.createdAt).toLocaleString()}</td>
                <td className="px-3 py-2 text-right">
                  <a href={filesApi.downloadUrl(f.id)} className="text-slate-600 mr-3">다운로드</a>
                  <button onClick={() => onDelete(f.id)} className="text-red-600">삭제</button>
                </td>
              </tr>
            ))}
            {rows.length === 0 && (
              <tr><td colSpan={6} className="px-3 py-8 text-center text-slate-400">파일이 없습니다.</td></tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="flex justify-center gap-2 mt-4">
        <button onClick={() => load(Math.max(0, page - 1))} disabled={page === 0} className="px-3 py-1 border rounded disabled:opacity-40">이전</button>
        <span className="px-3 py-1">{page + 1} / {totalPages}</span>
        <button onClick={() => load(page + 1)} disabled={page + 1 >= totalPages} className="px-3 py-1 border rounded disabled:opacity-40">다음</button>
      </div>
    </div>
  );
}
