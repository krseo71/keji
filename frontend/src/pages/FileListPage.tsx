import { ChangeEvent, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { filesApi, FileResponse, FileSource } from '../api/files';

const PAGE_SIZE = 20;

export default function FileListPage() {
  const [rows, setRows] = useState<FileResponse[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [source, setSource] = useState<FileSource | ''>('');
  const [keyword, setKeyword] = useState('');
  const [uploading, setUploading] = useState(false);
  const [uploadErr, setUploadErr] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);

  const onPickFile = async (e: ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (!f) return;
    setUploading(true); setUploadErr('');
    try {
      await filesApi.upload(f);
      await load(0);
    } catch (ex: any) {
      setUploadErr(ex.response?.data?.message ?? '업로드 실패');
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

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
        <input className="border border-slate-300 dark:border-slate-600 dark:bg-slate-800 rounded px-3 py-1" placeholder="이름/설명 검색" value={keyword} onChange={(e) => setKeyword(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && load(0)} />
        <select className="border border-slate-300 dark:border-slate-600 dark:bg-slate-800 rounded px-2 py-1" value={source} onChange={(e) => setSource(e.target.value as FileSource | '')}>
          <option value="">전체</option>
          <option value="MANUAL">수동 업로드</option>
          <option value="GENERATED">Claude 생성</option>
        </select>
        <button
          onClick={() => fileInputRef.current?.click()}
          disabled={uploading}
          className="border border-slate-300 dark:border-slate-700 rounded px-3 py-1 hover:bg-slate-50 dark:hover:bg-slate-800 disabled:opacity-50"
        >
          {uploading ? '업로드 중…' : '업로드'}
        </button>
        <Link to="/generate" className="bg-blue-600 hover:bg-blue-700 text-white rounded px-3 py-1">문서 생성</Link>
        <input
          ref={fileInputRef}
          type="file"
          className="hidden"
          onChange={onPickFile}
          accept=".hwp,.hwpx,.ppt,.pptx,.xlsx,.xls,.doc,.docx,.pdf,.csv,.md,.txt,.json"
        />
      </div>
      {uploadErr && <div className="text-red-600 dark:text-red-400 text-sm mb-2">{uploadErr}</div>}

      <div className="bg-white dark:bg-slate-900 rounded border border-slate-200 dark:border-slate-800 overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="border-b border-slate-200 dark:border-slate-800 text-slate-500 dark:text-slate-400">
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
              <tr key={f.id} className="border-t border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-700/60">
                <td className="px-3 py-2"><Link to={`/files/${f.id}`} className="text-slate-800 dark:text-slate-100 hover:underline">{f.originalName}</Link></td>
                <td className="px-3 py-2 uppercase">{f.extension}</td>
                <td className="px-3 py-2">{fmtSize(f.sizeBytes)}</td>
                <td className="px-3 py-2">
                  <span className={`text-xs px-2 py-0.5 rounded ${f.source === 'GENERATED' ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300' : 'bg-slate-200 text-slate-700 dark:bg-slate-700 dark:text-slate-200'}`}>
                    {f.source === 'GENERATED' ? 'Claude' : '수동'}
                  </span>
                </td>
                <td className="px-3 py-2">{new Date(f.createdAt).toLocaleString()}</td>
                <td className="px-3 py-2 text-right">
                  <button onClick={() => filesApi.download(f.id, f.originalName)} className="text-slate-600 dark:text-slate-300 mr-3">다운로드</button>
                  <button onClick={() => onDelete(f.id)} className="text-red-600 dark:text-red-400">삭제</button>
                </td>
              </tr>
            ))}
            {rows.length === 0 && (
              <tr><td colSpan={6} className="px-3 py-8 text-center text-slate-400 dark:text-slate-500">파일이 없습니다.</td></tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="flex justify-center gap-2 mt-4">
        <button onClick={() => load(Math.max(0, page - 1))} disabled={page === 0} className="px-3 py-1 border border-slate-300 dark:border-slate-600 rounded disabled:opacity-40">이전</button>
        <span className="px-3 py-1">{page + 1} / {totalPages}</span>
        <button onClick={() => load(page + 1)} disabled={page + 1 >= totalPages} className="px-3 py-1 border border-slate-300 dark:border-slate-600 rounded disabled:opacity-40">다음</button>
      </div>
    </div>
  );
}
