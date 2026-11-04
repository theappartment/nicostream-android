package com.nicostream.demo;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

final class ArtworkDisk {
    static byte[] load(File cache,String url)throws Exception{
        File dir=new File(cache,"artwork-v1");dir.mkdirs();StringBuilder name=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(url.getBytes("UTF-8")))name.append(String.format(Locale.ROOT,"%02x",b&255));File file=new File(dir,name.toString());
        if(file.isFile()&&file.length()<=4194304&&System.currentTimeMillis()-file.lastModified()<30L*86400000)return Files.readAllBytes(file.toPath());
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(10000);c.setInstanceFollowRedirects(false);byte[] bytes;
        try(InputStream input=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=input.read(b))!=-1){out.write(b,0,n);if(out.size()>4194304)throw new IOException("Immagine troppo grande");}bytes=out.toByteArray();}finally{c.disconnect();}
        try{File temp=File.createTempFile("image-",".tmp",dir);Files.write(temp.toPath(),bytes);if(!temp.renameTo(file))temp.delete();trim(dir);}catch(IOException ignored){}
        return bytes;
    }
    static synchronized void trim(File dir){File[] files=dir.listFiles();if(files==null)return;long size=0;for(File f:files)size+=f.length();if(size<=64L*1024*1024&&files.length<=500)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));int count=files.length;for(File f:files){if(size<=56L*1024*1024&&count<=450)break;long bytes=f.length();if(f.delete()){size-=bytes;count--;}}}
}
