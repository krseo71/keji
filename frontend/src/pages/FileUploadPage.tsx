import { ChangeEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { filesApi } from '../api/files';

export default function FileUploadPage() {
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const nav = useNavigate();

  const onPick = (e: ChangeEvent<HTMLInputElement>) => setFile(e.target.files?.[0] ?? null);
  const onUpload = async () => {
    if (!file) return;
    setBusy(true); setErr('');
    try {
      await filesApi.upload(file);
      nav('/');
    } catch (ex: any) {
      setErr(ex.response?.data?.message ?? '업로드 실패');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="max-w-md mx-auto bg-white dark:bg-slate-900 p-6 rounded border border-slate-200 dark:border-slate-800">
      <h1 className="text-xl font-bold mb-4">업로드</h1>
      <input type="file" onChange={onPick} className="mb-3" accept=".hwp,.hwpx,.ppt,.pptx,.xlsx,.xls,.doc,.docx,.pdf,.csv,.md,.txt,.json" />
      {file && <div className="text-sm text-slate-600 dark:text-slate-300 mb-3">선택: {file.name} ({(file.size / 1024).toFixed(1)}KB)</div>}
      {err && <div className="text-red-600 dark:text-red-400 text-sm mb-3">{err}</div>}
      <button disabled={!file || busy} onClick={onUpload} className="w-full bg-blue-600 hover:bg-blue-700 text-white rounded py-2 disabled:opacity-40">
        {busy ? '업로드 중…' : '업로드'}
      </button>
    </div>
  );
}
