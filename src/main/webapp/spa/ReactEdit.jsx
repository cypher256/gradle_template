/* React 編集コンポーネント */
const ReactEdit = () => {
	
	const router = useHistory(); // React Router v5 (v6 では useNavigate)
	const id = useParams().id;
	const isInsert = id == 0;
	const [form, setForm] = useState({});
	const [companySelect, setCompanySelect] = useState([]);
	useEffect(() => {handleInit()}, []);

	// 初期表示 → 取得 API 呼び出し   
	const handleInit = async() => {
		document.title = 'React の場合 (編集コンポーネント)';
		id_head_link_server.href = '../item/update?id=' + id;
		id_head_link_react.href = '../spa/react.html#/edit/' + id;
		id_head_link_vue.href   = '../spa/vue.html#/edit/' + id;
		id_message.textContent = null;
		if (!isInsert) {
			const data = (await axios.get('select?id=' + id)).data;
			if (typeof data === 'string') {
				id_message.textContent = data; // エラーメッセージ String
				router.push('/');
				return;
			} else {
				setForm(data); // ItemForm json
			}
		}
		setCompanySelect((await axios.get('select-company')).data);
		id_name.focus(); // jsx での autoFocus={true} だと初期値セットで onFocus が動作しないため
  	};
  	
  	// フォーム Enter → 登録・更新 API 呼び出し
	const handleSubmit = async(e) => {
		e.preventDefault(); // デフォルトサブミット抑止 (Vue は @submit.prevent)
		id_submit_button.disabled = true;
		const res = (await axios.post(isInsert ? 'insert' : 'update', params(id_form))); // axios が CSRF ヘッダ自動追加
		const errorMessage = res.data;
		if (errorMessage) {
			if (res.status == 200) {
				id_message.textContent = errorMessage; // 入力エラーなどのアプリエラー
				id_submit_button.disabled = false;
			} else {
				id_message.textContent = errorMessage; // 削除済みなどの 2xx システムエラー (2xx 以外は axios interceptors)
				router.push('/');
			}
		} else {
			id_message.textContent = `ℹ️ ${isInsert ? '登録' : '更新'}しました。`;
			router.push('/');
		}
  	};

	// 変更イベント → 入力チェック API 呼び出し   
	const handleChange = async() => {
		const errorMessage = (await axios.post('validate', params(id_form))).data;
		id_message.textContent = errorMessage; // エラーが無い場合は空
  	};

	return (
<HashRouter>
	<form onSubmit={handleSubmit} id="id_form" method="post">
		<input type="hidden" name="id" defaultValue={form.id}/>
		<div className="mb-3">
			<label className="form-label">製品名</label> <span className="badge bg-danger">必須</span>
			<input className="form-control" type="text" name="name" defaultValue={form.name} id="id_name"
				onFocus={e => e.target.setSelectionRange(99,99)}
				onChange={handleChange} required/>
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
			{/* select は制御コンポーネントでないと警告が出るため value と onChange 使用 (... はスプレッド構文) */}
			<select name="companyId" className="form-select w-auto" value={form.companyId}
				onChange={e => setForm({...form, companyId: e.target.value})}>
	{companySelect.map(com => (
				<option key={com.id} value={com.id}>{com.companyName}</option>
	))}
			</select>
		</div>
		<Link to="/" className="btn btn-secondary px-5 me-1">戻る</Link>
		<input id="id_submit_button" type="submit" className="btn btn-warning px-5" value={isInsert ? '登録' : '更新'}/>
	</form>
</HashRouter>
	);
}
