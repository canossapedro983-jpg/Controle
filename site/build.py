# Gera a versão do site (Supabase) a partir do app do artefato.
import sys
src=open("/home/claude/app/index.html").read()
for a in ['\n<link rel="manifest" href="manifest.json">','\n<link rel="apple-touch-icon" href="icon-192.png">','try{if("serviceWorker" in navigator)navigator.serviceWorker.register("sw.js").catch(()=>{})}catch(e){}\n']:
    src=src.replace(a,'')
a=src.index('(async()=>{\n  try{\n    if(OFFLINE)return;\n    if(!(window.claude&&claude.use))return;')
b=src.index('})();\n',a)+len('})();\n')
inicio=src[a:b]
# corpo depois de obter "col": reaproveita a lógica de fila/snapshot existente
c=inicio.index('    const snap=await col.get();')
d=inicio.index('  }catch(e){syncMsg("Salvo só neste aparelho")}')
corpo=inicio[c:d]
novo='''const SB_URL="https://gpjsvrvuchmicyiuczcf.supabase.co",SB_KEY="sb_publishable_WahALizhzR61l4AJniCPdA_Hcdeg3QP";
function carregaSB(){return new Promise((ok,err)=>{if(window.supabase)return ok();const s=document.createElement("script");s.src="https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2.45.4/dist/umd/supabase.js";s.onload=ok;s.onerror=err;document.head.appendChild(s)})}
function colSupabase(SB,uid){
  // Lançamentos ("i_...") ficam na tabela "lancamentos"; o resto (config, pendentes) fica em "docs".
  const ehLanc=id=>/^i_/.test(id);
  const paraLinha=(id,v)=>{const {tipo,desc,valor,data,hora,cat,pag,...extra}=v||{};return{user_id:uid,id,tipo:tipo||"out",descricao:desc||"",valor:Math.round((Number(valor)||0)*100)/100,data,hora:hora||"",categoria:cat||"",pagamento:pag||"",extra,atualizado_em:new Date().toISOString()}};
  const deLinha=r=>{const ex=r.extra||{};return{...ex,id:ex.id!==undefined?ex.id:r.id,tipo:r.tipo,desc:r.descricao,valor:Number(r.valor),data:r.data,hora:r.hora||"",cat:r.categoria||"",pag:r.pagamento||""}};
  const tudo=async(tab,cols)=>{let rows=[],k=0;for(;;){const {data,error}=await SB.from(tab).select(cols).range(k,k+999);if(error)throw error;rows=rows.concat(data);if(data.length<1000)break;k+=1000}return rows};
  const col={
    doc:id=>({
      async set(v){const {error}=ehLanc(id)?await SB.from("lancamentos").upsert(paraLinha(id,v)):await SB.from("docs").upsert({user_id:uid,id,data:v,updated_at:new Date().toISOString()});if(error)throw error},
      async delete(){const {error}=await SB.from(ehLanc(id)?"lancamentos":"docs").delete().eq("id",id);if(error)throw error},
      async get(){if(ehLanc(id)){const {data}=await SB.from("lancamentos").select("*").eq("id",id).maybeSingle();return{exists:!!data,data:()=>data?deLinha(data):undefined}}
        const {data}=await SB.from("docs").select("data").eq("id",id).maybeSingle();return{exists:!!data,data:()=>data?data.data:undefined}}
    }),
    async get(){
      const [docs,lanc]=await Promise.all([tudo("docs","id,data"),tudo("lancamentos","*")]);
      const vistos=new Set(lanc.map(r=>r.id));
      const out=lanc.map(r=>({id:r.id,data:()=>deLinha(r)}));
      // documentos antigos de lançamento (antes da tabela) continuam aparecendo até serem migrados
      docs.forEach(r=>{if(ehLanc(r.id)&&vistos.has(r.id))return;out.push({id:r.id,data:()=>r.data})});
      return{docs:out};
    },
    onSnapshot(cb,err){
      let t=null,ocupado=false;
      const load=async()=>{if(ocupado)return;ocupado=true;try{cb(await col.get())}catch(e){err&&err(e)}ocupado=false};
      load();
      const ch=SB.channel("dados-"+uid);
      ["docs","lancamentos"].forEach(tb=>ch.on("postgres_changes",{event:"*",schema:"public",table:tb,filter:"user_id=eq."+uid},()=>{clearTimeout(t);t=setTimeout(load,500)}));
      ch.subscribe();
      document.addEventListener("visibilitychange",()=>{if(!document.hidden)load()});
      setInterval(()=>{if(!document.hidden)load()},60000);
    }
  };
  return col;
}
function telaLogin(SB){
  return new Promise(ok=>{
    const L=$("login");L.hidden=false;const m=t=>$("lgMsg").textContent=t;
    const pega=()=>({email:$("lgEmail").value.trim(),password:$("lgSenha").value});
    $("lgEntrar").onclick=async()=>{const c=pega();if(!c.email||!c.password)return m("Preencha e-mail e senha.");m("Entrando…");
      const {data,error}=await SB.auth.signInWithPassword(c);
      if(error)return m(/confirm/i.test(error.message)?"Confirme seu e-mail primeiro (veja sua caixa de entrada).":"E-mail ou senha incorretos.");
      L.hidden=true;ok(data.user)};
    $("lgCriar").onclick=async()=>{const c=pega();if(!c.email||c.password.length<6)return m("Use um e-mail válido e senha com pelo menos 6 caracteres.");m("Criando conta…");
      const {data,error}=await SB.auth.signUp({...c,options:{emailRedirectTo:location.origin+location.pathname}});
      if(error)return m(/registered/i.test(error.message)?"Esse e-mail já tem conta. Toque em Entrar.":"Não foi possível criar a conta: "+error.message);
      if(data.session){L.hidden=true;ok(data.user)}else m("Conta criada! Abra o e-mail de confirmação que enviamos, toque no link e depois volte aqui e toque em Entrar.")};
    $("lgEsqueci").onclick=async()=>{const e=$("lgEmail").value.trim();if(!e)return m("Digite seu e-mail acima.");await SB.auth.resetPasswordForEmail(e,{redirectTo:location.origin+location.pathname});m("Se o e-mail tiver conta, enviamos um link para criar nova senha.")};
  });
}
(async()=>{
  try{
    if(OFFLINE)return;
    syncMsg("Conectando…");
    await carregaSB();
    const SB=supabase.createClient(SB_URL,SB_KEY,{auth:{persistSession:true,autoRefreshToken:true,detectSessionInUrl:true}});
    SB.auth.onAuthStateChange((ev)=>{if(ev==="PASSWORD_RECOVERY"){const n=prompt("Digite a nova senha (mínimo 6 caracteres):");if(n&&n.length>=6)SB.auth.updateUser({password:n}).then(()=>alert("Senha alterada."))}});
    let {data:{session}}=await SB.auth.getSession();
    let user=session&&session.user;
    if(!user)user=await telaLogin(SB);
    const uid=user.id;
    $("contaBox").hidden=false;$("contaEmail").textContent="Conta: "+user.email;
    $("sair").onclick=async()=>{await SB.auth.signOut();try{localStorage.removeItem(KEY)}catch(e){}location.reload()};
    const col=colSupabase(SB,uid);
'''+corpo.replace('syncMsg("Sincronizado com sua conta")','syncMsg("Sincronizado com sua conta")')+'''  }catch(e){syncMsg("Sem conexão com o servidor. Salvo só neste aparelho.")}
})();
'''
src=src[:a]+novo+src[b:]
# login overlay + estilos
src=src.replace('<main>','''<div id="login" hidden style="position:fixed;inset:0;z-index:50;background:var(--bg);display:grid;place-items:center;padding:16px;overflow:auto">
  <div class="panel" style="width:100%;max-width:380px">
    <h2 style="font-size:1.4rem">Controle de gastos</h2>
    <p class="meta" style="margin:0 0 16px">Entre para ver seus lançamentos em qualquer aparelho.</p>
    <label for="lgEmail">E-mail</label><input id="lgEmail" type="email" autocomplete="email" inputmode="email">
    <div style="margin-top:12px"><label for="lgSenha">Senha</label><input id="lgSenha" type="password" autocomplete="current-password"></div>
    <button class="btn" id="lgEntrar" type="button">Entrar</button>
    <button class="ghost" id="lgCriar" type="button" style="width:100%;margin-top:8px;min-height:44px">Criar conta</button>
    <button class="ghost" id="lgEsqueci" type="button" style="width:100%;margin-top:8px;min-height:36px;border:0">Esqueci a senha</button>
    <p class="meta" id="lgMsg" role="status"></p>
  </div>
</div>
<main>''',1)
# Gmail via Claude não existe fora do claude.ai: deixa o APK ler as notificações do Gmail de novo
src=src.replace('return a.filter(n=>{if(/android\\.gm/i.test(n.pkg||"")){try{AND.removerNotif(n.id)}catch(e){}return false}return true})}','return a}')
src=src.replace('const GMAIL_NOTIF=false;','const GMAIL_NOTIF=true;',1)
# Busca imediata no Gmail (Apps Script) ao abrir o app e a aba Aprovar
src=src.replace('window.atualizaAprovar=function(){','''const GS_URL="https://script.google.com/macros/s/AKfycbwtGNCrC2WN4B2wEWQyyxsw8n2b2UidlgdYbjlH0xGh7LcDqJL0txD40FOC70dGpeDF/exec";
let gsUlt=0;function pedirBuscaGmail(){if(OFFLINE||!PREFS.gmail||Date.now()-gsUlt<20000)return;gsUlt=Date.now();try{fetch(GS_URL,{mode:"no-cors"}).catch(()=>{})}catch(e){}}
window.atualizaAprovar=function(){
  if(!$("vA").hidden)pedirBuscaGmail();''',1)
src=src.replace('    const col=colSupabase(SB,uid);','    const col=colSupabase(SB,uid);pedirBuscaGmail();',1)
open("/home/claude/site/index.html","w").write(src)
print("ok", "removerNotif(n.id)}catch" in src)
