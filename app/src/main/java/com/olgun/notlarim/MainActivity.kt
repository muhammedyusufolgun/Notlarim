package com.olgun.notlarim

import android.app.*
import android.appwidget.*
import android.content.*
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

data class Note(val id: Long, var title: String, var body: String)
object Store {
 fun load(c: Context): MutableList<Note> {
  val a=JSONArray(c.getSharedPreferences("notes",0).getString("items","[]"))
  return MutableList(a.length()){i->val x=a.getJSONObject(i);Note(x.getLong("id"),x.getString("title"),x.getString("body"))}
 }
 fun save(c:Context,notes:List<Note>){
  val a=JSONArray();notes.forEach{a.put(JSONObject().put("id",it.id).put("title",it.title).put("body",it.body))}
  c.getSharedPreferences("notes",0).edit().putString("items",a.toString()).apply()
  NotesWidget.refresh(c)
 }
}
class NotesWidget:AppWidgetProvider(){
 override fun onUpdate(c:Context,m:AppWidgetManager,ids:IntArray){ids.forEach{update(c,m,it)}}
 companion object {
  fun refresh(c:Context){val m=AppWidgetManager.getInstance(c);val ids=m.getAppWidgetIds(ComponentName(c,NotesWidget::class.java));ids.forEach{update(c,m,it)}}
  fun update(c:Context,m:AppWidgetManager,id:Int){
   val notes=Store.load(c);val selected=c.getSharedPreferences("notes",0).getLong("widget",-1)
   val n=notes.find{it.id==selected}?:notes.firstOrNull()
   val v=RemoteViews(c.packageName,R.layout.widget)
   v.setTextViewText(R.id.widget_title,n?.title?:"Notlarım")
   v.setTextViewText(R.id.widget_body,n?.body?:"Henüz not eklenmedi")
   val p=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
   v.setOnClickPendingIntent(R.id.widget_body,p);m.updateAppWidget(id,v)
  }
 }
}
class MainActivity:Activity(){
 private val notes=mutableListOf<Note>()
 private lateinit var list:LinearLayout
 private var exportBytes=ByteArray(0)
 private var exportType="text/plain"
 private var exportName="Notlar.txt"
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode==42 && resultCode==RESULT_OK){val uri=data?.data?:return;try{contentResolver.openOutputStream(uri)?.use{it.write(exportBytes)};Toast.makeText(this,"Kaydedildi",Toast.LENGTH_SHORT).show()}catch(e:Exception){Toast.makeText(this,"Kaydedilemedi: ${e.message}",Toast.LENGTH_LONG).show()}}}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);notes.addAll(Store.load(this));render()}
 private fun btn(text:String, action:()->Unit):Button=Button(this).apply{this.text=text;setOnClickListener{action()}}
 private fun render(){
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(14,12,14,8);setBackgroundColor(Color.rgb(248,250,252))}
  val heading=TextView(this).apply{text="Notlarım";textSize=25f;setTextColor(Color.rgb(2,132,199));gravity=Gravity.CENTER;setPadding(12,12,12,12)}
  root.addView(heading)
  val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  bar.addView(btn("＋ Yeni Not"){edit(null)},LinearLayout.LayoutParams(0,-2,1f))
  bar.addView(btn("Tümünü Aktar"){chooseExport(notes)},LinearLayout.LayoutParams(0,-2,1f))
  root.addView(bar)
  val scroll=ScrollView(this);list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};scroll.addView(list);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  setContentView(root);drawNotes()
 }
 private fun drawNotes(){
  list.removeAllViews()
  notes.forEachIndexed{index,n->
   val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(12,8,12,8);setBackgroundColor(Color.WHITE)}
   val title=TextView(this).apply{text=n.title;if(text.isBlank())text="Başlıksız";textSize=19f;setTextColor(Color.rgb(20,70,105))}
   card.addView(title)
   card.addView(TextView(this).apply{text=n.body.take(180);maxLines=4;textSize=15f})
   val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
   actions.addView(btn("Düzenle"){edit(n)},LinearLayout.LayoutParams(0,-2,1f))
   actions.addView(btn("Aktar"){chooseExport(listOf(n))},LinearLayout.LayoutParams(0,-2,1f))
   actions.addView(btn("Sil"){delete(n)},LinearLayout.LayoutParams(0,-2,1f))
   card.addView(actions)
   val options=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
   options.addView(btn("↑"){move(index,-1)},LinearLayout.LayoutParams(0,-2,1f))
   options.addView(btn("↓"){move(index,1)},LinearLayout.LayoutParams(0,-2,1f))
   options.addView(btn("Widget'ta göster"){getSharedPreferences("notes",0).edit().putLong("widget",n.id).apply();NotesWidget.refresh(this);Toast.makeText(this,"Widget notu seçildi",Toast.LENGTH_SHORT).show()},LinearLayout.LayoutParams(0,-2,2f))
   card.addView(options)
   list.addView(card,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=10})
  }
 }
 private fun move(i:Int,offset:Int){val j=i+offset;if(j !in notes.indices)return;val n=notes.removeAt(i);notes.add(j,n);Store.save(this,notes);drawNotes()}
 private fun edit(note:Note?){
  val layout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(22,12,22,0)}
  val title=EditText(this).apply{hint="Not başlığı";setSingleLine(true);setText(note?.title?:"")}
  val body=EditText(this).apply{hint="Not içeriği";minLines=8;gravity=Gravity.TOP;setText(note?.body?:"")}
  layout.addView(title);layout.addView(body)
  AlertDialog.Builder(this).setTitle(if(note==null)"Yeni Not" else "Notu Düzenle").setView(layout).setNegativeButton("Vazgeç",null).setPositiveButton("Kaydet"){_,_->
   if(note==null)notes.add(0,Note(System.currentTimeMillis(),title.text.toString(),body.text.toString()))
   else{note.title=title.text.toString();note.body=body.text.toString()}
   Store.save(this,notes);drawNotes()
  }.show()
 }
 private fun delete(n:Note){
  AlertDialog.Builder(this).setTitle("Not silinsin mi?").setMessage(n.title).setNegativeButton("Vazgeç",null).setPositiveButton("Devam"){_,_->
   AlertDialog.Builder(this).setTitle("İkinci onay").setMessage("Bu not kalıcı olarak silinecek. Emin misiniz?").setNegativeButton("İptal",null).setPositiveButton("Kalıcı Sil"){_,_->notes.removeAll{it.id==n.id};Store.save(this,notes);drawNotes()}.show()
  }.show()
 }
 private fun chooseExport(items:List<Note>){
  if(items.isEmpty()){Toast.makeText(this,"Not bulunamadı",Toast.LENGTH_SHORT).show();return}
  val formats=arrayOf("PDF","TXT","JPG")
  AlertDialog.Builder(this).setTitle("Dışa aktar").setItems(formats){_,which->
   val format=formats[which];val text=items.joinToString("\n\n────────\n\n"){"${it.title}\n\n${it.body}"}
   exportType=when(format){"PDF"->"application/pdf";"JPG"->"image/jpeg";else->"text/plain"}
   exportBytes=when(format){"PDF"->pdf(text);"JPG"->jpg(text);else->text.toByteArray(Charsets.UTF_8)}
   exportName=(if(items.size==1)items[0].title.take(35).ifBlank{"Not"} else "Tum_Notlar")+"."+format.lowercase()
   startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type=exportType;putExtra(Intent.EXTRA_TITLE,exportName)},42)
  }.show()
 }
 private fun lines(text:String,paint:Paint,maxWidth:Int):List<String>{
  val out=mutableListOf<String>()
  text.split("\n").forEach{paragraph->
   var current=""
   paragraph.split(" ").forEach{word->
    val next=if(current.isEmpty())word else current+" "+word
    if(paint.measureText(next)>maxWidth && current.isNotEmpty()){out.add(current);current=word}else current=next
   };out.add(current)
  };return out
 }
 private fun pdf(text:String):ByteArray{
  val p=PdfDocument();val paint=Paint(3).apply{color=Color.BLACK;textSize=13f}
  val ls=lines(text,paint,535);var pageNo=1;var at=0
  while(at<ls.size){val page=p.startPage(PdfDocument.PageInfo.Builder(595,842,pageNo++).create());var y=42f
   while(at<ls.size && y<805){page.canvas.drawText(ls[at++],30f,y,paint);y+=19}
   p.finishPage(page)
  }
  val out=ByteArrayOutputStream();p.writeTo(out);p.close();return out.toByteArray()
 }
 private fun jpg(text:String):ByteArray{
  val paint=Paint(3).apply{color=Color.BLACK;textSize=28f}
  val ls=lines(text,paint,1000);val bitmap=Bitmap.createBitmap(1080,(ls.size*40+60).coerceIn(120,16000),Bitmap.Config.RGB_565)
  val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE)
  ls.forEachIndexed{i,s->val y=45f+i*40;if(y<bitmap.height)canvas.drawText(s,30f,y,paint)}
  val out=ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,95,out);bitmap.recycle();return out.toByteArray()
 }
}
