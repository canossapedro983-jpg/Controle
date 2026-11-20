/**
 * Controle de gastos — busca Pix do PicPay no Gmail e manda para a aba "Aprovar".
 * Roda na sua conta Google (script.google.com). Lê só e-mails de no-reply@picpay.com com "Pix" no assunto.
 */
const SUPABASE_URL = 'https://gpjsvrvuchmicyiuczcf.supabase.co';
const SUPABASE_KEY = 'sb_publishable_WahALizhzR61l4AJniCPdA_Hcdeg3QP';
const TOKEN = "COLE_SEU_TOKEN_AQUI"; // chave secreta: não compartilhe

function buscarPix() {
  const threads = GmailApp.search('from:no-reply@picpay.com subject:pix newer_than:2d', 0, 20);
  threads.forEach(function (t) {
    t.getMessages().forEach(function (m) {
      const id = m.getId();
      const assunto = m.getSubject() || '';
      const corpo = (m.getPlainBody() || '').replace(/\s+/g, ' ');
      const mv = corpo.match(/R\$\s*([\d.]+,\d{2})/);
      if (!mv) return;
      const valor = Number(mv[1].replace(/\./g, '').replace(',', '.'));
      const nome = corpo.match(/Pix (?:para|de)\s+(.+?)\s+Valor/i);
      const tipo = /recebid/i.test(assunto) ? 'in' : 'out';
      const desc = (nome ? nome[1].trim() : (/devolu/i.test(assunto) ? 'Devolução Pix' : 'Pix')).slice(0, 60);
      const dt = corpo.match(/(\d{2})\/(\d{2})\/(\d{4})\s*às\s*(\d{2}:\d{2})/);
      const d = m.getDate();
      const data = dt ? dt[3] + '-' + dt[2] + '-' + dt[1] : Utilities.formatDate(d, 'America/Sao_Paulo', 'yyyy-MM-dd');
      const hora = dt ? dt[4] : Utilities.formatDate(d, 'America/Sao_Paulo', 'HH:mm');
      const dados = {
        tipo: tipo, desc: desc, valor: valor, data: data, hora: hora,
        cat: tipo === 'in' ? 'Receita' : 'Outros', pag: 'Pix',
        raw: assunto + ' — ' + corpo.slice(0, 160)
      };
      UrlFetchApp.fetch(SUPABASE_URL + '/rest/v1/rpc/inserir_pendente', {
        method: 'post', contentType: 'application/json', muteHttpExceptions: true,
        headers: { apikey: SUPABASE_KEY, Authorization: 'Bearer ' + SUPABASE_KEY },
        payload: JSON.stringify({ p_token: TOKEN, p_id: id, p_dados: dados })
      });
    });
  });
}

/** Rode esta função UMA vez: a busca passa a acontecer sozinha a cada 1 minuto (o menor intervalo do Google). */
function ativarBuscaAutomatica() {
  ScriptApp.getProjectTriggers().forEach(function (t) { ScriptApp.deleteTrigger(t); });
  ScriptApp.newTrigger('buscarPix').timeBased().everyMinutes(1).create();
  buscarPix();
}

/** Permite que o app peça uma busca na hora (quando você abre a aba Aprovar). */
function doGet(e) {
  // Só dispara a busca na sua própria caixa; não devolve nenhum dado.
  buscarPix();
  return ContentService.createTextOutput('ok');
}
