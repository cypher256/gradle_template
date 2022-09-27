const { useState, useEffect } = React;
const { HashRouter, Route, Link, useParams, useHistory } = ReactRouterDOM;

/* 詳細コンポーネント */
const Detail = () => {
	
	const [form, setForm] = useState({});
	const [companyId, setCompanyId] = useState();
	const [companySelect, setCompanySelect] = useState([]);
	const [message, setMessage] = useState();
	const history = useHistory();
	const id = useParams().id;
	const isInsert = id == 0;
	useEffect(() => {handleInit()}, []);

	// 初期表示 → 取得 API 呼び出し   
	const handleInit = async() => {
		if (!isInsert) {
			const resData = (await axios.get('detail?id=' + id)).data;
			if (typeof resData === 'string') {
				AppState.message = resData; // エラーメッセージ String
				history.push('/');
				return;
			} else {
				setForm(resData); // ItemForm json
				setCompanyId(resData.companyId);
			}
		}
		setCompanySelect((await axios.get('companySelect')).data);
  	};
  	
  	// フォーム Enter → 登録・更新 API 呼び出し
	const handleSubmit = async(e) => {
		e.preventDefault(); // デフォルトサブミット抑止
		_submitButton.disabled = true;
		const res = (await axios.post(isInsert ? 'insert' : 'update', new URLSearchParams(new FormData(_form))));
		const errorMessage = res.data;
		if (errorMessage) {
			if (res.status == 200) {
				setMessage(errorMessage); // 復旧可能なアプリエラー (入力エラー)
				_submitButton.disabled = false;
			} else {
				AppState.message = errorMessage; // 復旧不可のシステムエラー (削除済みなど)
				history.push('/');
			}
		} else {
			AppState.message = `ℹ️ ${isInsert ? '登録' : '更新'}しました。`;
			history.push('/');
		}
  	};

	// 変更イベント → 入力チェック API 呼び出し   
	const handleChange = async() => {
		const errorMessage = (await axios.post('validate', new URLSearchParams(new FormData(_form)))).data;
		setMessage(errorMessage); // エラーが無い場合は空
  	};

	return (
<HashRouter>
 	<div className="alert mb-0" style={{minHeight:'4rem'}}>{message}</div>
	<form id="_form" method="post" onSubmit={handleSubmit}>
		<input type="hidden" name="id" defaultValue={form.id}/>
		<div className="mb-3">
			<label className="form-label">製品名</label> <span className="badge bg-danger">必須</span>
			<input className="form-control" type="text" name="name" defaultValue={form.name} size="40"
				onChange={handleChange} required autoFocus/>
		</div>
		<div className="mb-3">
			<label className="form-label">発売日</label> <span className="badge bg-danger">必須</span>
			<input className="form-control w-auto" type="date" name="releaseDate" defaultValue={form.releaseDate}
				onChange={handleChange} required/>
		</div>
		<div className="mb-3 form-check">
			<input type="checkbox" name="faceAuth" id="faceAuth" className="form-check-input"
				onChange={handleChange} defaultChecked={form.faceAuth}/>
			<label className="form-check-label" htmlFor="faceAuth">顔認証</label>
		</div>
		<div className="mb-5">
			<label className="form-label">メーカー</label>
			<select name="companyId" className="form-select w-auto" value={companyId}
				onChange={e => setCompanyId(e.target.value)}>
	{companySelect.map(com => (
				<option key={com.id} value={com.id}>{com.companyName}</option>
	))}
			</select>
		</div>
		<Link to="/" className="btn btn-secondary px-5 me-1">戻る</Link>
		<input id="_submitButton" type="submit" className="btn btn-warning px-5" value={isInsert ? '登録' : '更新'}/>
	</form>
</HashRouter>
	);
}
