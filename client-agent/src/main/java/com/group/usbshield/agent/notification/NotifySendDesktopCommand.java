package com.group.usbshield.agent.notification;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Member 2 — Week 2: only invoke in the authenticated graphical user session. */
public class NotifySendDesktopCommand {
    public boolean execute(String title,String body) {
        Process process=null;
        try {
            process=new ProcessBuilder("/usr/bin/notify-send","-a","USBShield","-u","normal",title,body).start();
            if(!process.waitFor(3,TimeUnit.SECONDS)) {process.destroyForcibly();return false;}
            return process.exitValue()==0;
        } catch (IOException e) {return false;}
        catch (InterruptedException e) {Thread.currentThread().interrupt();return false;}
        finally {if(process!=null&&process.isAlive())process.destroyForcibly();}
    }
}
