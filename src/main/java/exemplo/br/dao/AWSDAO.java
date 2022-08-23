package exemplo.br.dao;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.apache.commons.codec.binary.Base64;

import exemplo.br.model.Anexo;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.RawMessage;
import software.amazon.awssdk.services.ses.model.SendRawEmailRequest;

public class AWSDAO {
 /**
  * Trabalha a lista de anexos atribuindo o conteúdo Base64 ao dataHandler, o
  * nome do arquivo ao FileName e adiciona o anexo ao MimeMultipart que será
  * retornado.<br>
  * <br>
  * Caso queira adicionar mais Content Types especificos é so adicionar mais
  * cases no switch.<br>
  * <br>
  * O encoding da string Base64 contida no Anexo deve ter sido feita utilizando
  * a mesma biblioteca que está sendo usada para fazer decoding, no caso 
  * abaixo está sendo usado org.apache.commons.<br>
  * <br> 
  * @param lstAnexos
  * @return MimeMultipart do tipo "mixed" contendo varios MimeBodyPart de
  * anexos.
  */
 private static MimeMultipart prepararAnexosEmail(ArrayList<Anexo> lstAnexos){
  MimeMultipart corpoComAnexos=new MimeMultipart("mixed");
  try{
   for(Anexo anexo:lstAnexos){
    String sNomeArquivo=anexo.getNomeArquivo();
    String sContentType="application/octet-stream";
    MimeBodyPart anexosEmail=new MimeBodyPart();
    String[] nomeArquivoSplit=sNomeArquivo.split("\\.");
    switch(nomeArquivoSplit[1]){
     case "txt": sContentType="text/plain"; break;
     case "xml": sContentType="application/xml"; break;
    }
    DataSource fds = new ByteArrayDataSource(Base64.decodeBase64(anexo.getBase64()),sContentType);
    anexosEmail.setDataHandler(new DataHandler(fds));
    anexosEmail.setFileName(sNomeArquivo);
    corpoComAnexos.addBodyPart(anexosEmail);
   }
  }catch(MessagingException e){
   System.out.println("Erro ao preparar anexos: "+e);
  }
  return corpoComAnexos;
 }
 
 /**
  * Cria um objeto Message, atribui Subject, From, Recipients e Content
  * que será usado pelo SesClient para enviar um email.<br>
  * O metodo falha nas seguintes situações:<br>
  * <br>
  * <ul>
  *   <li>sRemetente.equals("") ou inválido</li>
  *   <li>sDestinatario.equals("") ou inválido</li>
  * </ul>
  * @param sRemetente
  * @param sDestinatario
  * @param sAssunto
  * @param sMensagem
  * @param lstAnexos
  * @return Objeto Message configurado e pronto para uso do SesClient
  */
  private static Message prepararMensagemEmail(String sRemetente,String sDestinatario,String sAssunto,String sMensagem,ArrayList<Anexo> lstAnexos){
   Properties properties=new Properties();
   Session session=Session.getDefaultInstance(properties);
   Message message=new MimeMessage(session);
   MimeMultipart corpoTexto = new MimeMultipart("alternative");
   MimeBodyPart mensagemEmail = new MimeBodyPart();
   MimeMultipart corpoComAnexos = new MimeMultipart("mixed");
   MimeBodyPart containerEmail = new MimeBodyPart();
   try {
    message.setSubject(sAssunto);
    message.setFrom(new InternetAddress(sRemetente));
    message.setRecipient(Message.RecipientType.TO,new InternetAddress(sDestinatario));
    if(sMensagem.toLowerCase().contains("<html")){
     mensagemEmail.setContent(sMensagem,"text/html; charset=UTF-8");
    }else{
     mensagemEmail.setContent(sMensagem,"text/plain; charset=ISO_8859_1");
    }
    corpoTexto.addBodyPart(mensagemEmail);
    if(!lstAnexos.isEmpty()){
     containerEmail.setContent(corpoTexto);
     corpoComAnexos=prepararAnexosEmail(lstAnexos);
     corpoComAnexos.addBodyPart(containerEmail);
    }
    message.setContent(corpoComAnexos);
   } catch (MessagingException e) {
    System.out.println("Erro ao configurar mensagem: "+e);
   }
   return message;
  }
 /**
  * Desconstrui a lstParametros separando sRemetente, sDestinatario, sAssunto,
  * sMensagem. Invoca o metodo prepararMensagemEmail para receber o objeto 
  * Message que será usado pelo SesClient. Inicia o SesClient utilizando o
  * arquivo credentials e config da pasta .aws no diretório home da maquina.
  * Converte o objeto Message para uma stream de bytes e envia o email.
  * 
  * @param lstParametros
  * @param lstAnexos
  */
 public static void EnviarEmail(ArrayList<String> lstParametros,ArrayList<Anexo> lstAnexos){
  String sRemetente=lstParametros.get(0);
  String sDestinatario=lstParametros.get(1);
  String sAssunto=lstParametros.get(2);
  String sMensagem=lstParametros.get(3);
  Message message=prepararMensagemEmail(sRemetente,sDestinatario,sAssunto,sMensagem,lstAnexos);
  SesClient client=SesClient.builder().build();
  try{
   ByteArrayOutputStream outputStream=new ByteArrayOutputStream();
   message.writeTo(outputStream);
   ByteBuffer byteBuffer=ByteBuffer.wrap(outputStream.toByteArray());
   byte[] arr=new byte[byteBuffer.remaining()];
   byteBuffer.get(arr);
   SdkBytes data=SdkBytes.fromByteArray(arr);
   RawMessage rawMessage=RawMessage.builder().data(data).build();
   SendRawEmailRequest rawEmailRequest=SendRawEmailRequest.builder().rawMessage(rawMessage).build();
   client.sendRawEmail(rawEmailRequest);
   client.close();
  }catch(Exception e){
   System.out.println("Erro ao enviar e-mail: "+e);
  }
 }
 public static void main(String[] args){
  ArrayList<String> lstParametros=new ArrayList<String>();
  // Antes de executar altere os e-mails de remetente e destinatário

  lstParametros.add("emailremetente@gmail.com");                         // Remetente
  lstParametros.add("emaildestinatario@gmail.com");                      // Destinatário
  lstParametros.add("AWS SES no Java");                                  // Assunto
  lstParametros.add("Este e-mail foi enviado pelo Java usando AWS SES"); // Mensagem
  
  // Exemplo enviando HTML
  /*
  lstParametros.add("<html>"+
                    " <h1 style=\"color: blue\">E-mail com HTML</h1>"+
                    " <p>Este e-mail foi enviado via AWS SES utilizando Java 1.8</p>"+
                    "</html>");
  */
  ArrayList<Anexo> lstAnexos=new ArrayList<Anexo>();
  String[] lstArquivos={"Teste.txt","Teste2.xml"};
  try{
   for(String arquivo: lstArquivos){
    String sPathArquivos="src/main/resources/";
    String sNomeArquivo=sPathArquivos+arquivo;
    Anexo anexo=new Anexo();
    anexo.setBase64(Base64.encodeBase64String(Files.readAllBytes(Paths.get(sNomeArquivo))));
    anexo.setNomeArquivo(arquivo);
    lstAnexos.add(anexo);
   }
  }catch(IOException e){
   System.out.println("Arquivos não encontrados: "+e);
  }
  EnviarEmail(lstParametros,lstAnexos);
 }
}
