import org.jnativehook.GlobalScreen;
import org.jnativehook.keyboard.NativeKeyEvent;
import org.jnativehook.keyboard.NativeKeyListener;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.awt.event.KeyEvent.VK_ESCAPE;


/**
 * Created by Вова on 19.09.2017.
 */
public class Controller {

    private AtomicBoolean cancelation ;
    private AtomicBoolean promtIsActive;

    Controller(int portNumber) throws IOException {

        // add KeyListener for ESC termination
        globalListenerManager keyLisMan = new globalListenerManager();
        GlobalScreen.addNativeKeyListener(new EscapeListener());

        promtIsActive = new AtomicBoolean(false);
        cancelation  =  new AtomicBoolean(false);
        
        //Read X
        Scanner reader = new Scanner(System.in);  // Reading from System.in
        System.out.println("Enter a number: ");
        String x = reader.next();

        /*
             x = [1..6] corresponds 1-6 tests for ESC cancelation
        */

        // stuff (starting F and G)
        String[] functions = {"F", "G"};
        List<Process> processes = new LinkedList<Process>();
        for(String function : functions) {
            String[] command = {"java", "Main", function};
            ProcessBuilder probuilder = new ProcessBuilder( command );
            probuilder.directory(new File(System.getProperty("user.dir") +  "\\target\\classes"));
            processes.add(probuilder.start());
        }


        //Creating Server Socket and executor
        ExecutorService executor = Executors.newFixedThreadPool(2);

        ServerSocket serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(InetAddress.getLocalHost().getHostAddress(), portNumber));

        //ServerSocket serverSocket2 = new ServerSocket();
        //serverSocket2.bind(new InetSocketAddress(InetAddress.getLocalHost().getHostAddress(), portNumber2));

        try (
                Socket socket = serverSocket.accept();
                Socket socket2 = serverSocket.accept();
        ) {

            // new thread for a client;
            Future<Server.Status> future1 = executor.submit(new Server(socket,Integer.parseInt(x),"F"));
            Future<Server.Status> future2 = executor.submit(new Server(socket2,Integer.parseInt(x),"G"));

            //Promt loop
            boolean PopingUserPromt = true;
            final long WAIT_TIME = 3000000000L;
            long time = System.nanoTime() + WAIT_TIME;

            while(!cancelation.get() && ( (!future1.isDone()) || !future2.isDone())){

                Thread.sleep(1);  // added to reduce CPU usage (cutting down is significant: from 25% to 1%)
                if(future1.isDone() && !future1.get().getResult()) break;
                if(future2.isDone() && !future2.get().getResult()) break;
                if(!PopingUserPromt) continue;
                if(cancelation.get()) break;

                if(System.nanoTime() < time) continue;

                promtIsActive.set(true);
                System.out.println("Computation is still uncomplete \n Choose one of the following commands: \n (a)continue \n (b)continue without promt \n (c)cancel");
                String UserCommand = reader.next();

                switch (UserCommand) {
                    case "a":
                        //System.out.println("Continue calculation");
                        time = System.nanoTime() + WAIT_TIME;
                        promtIsActive.set(false);
                        continue;
                    case "b":
                        PopingUserPromt = false;
                        System.out.println("No promt option");
                        promtIsActive.set(false);
                        continue;
                    case "c":
                        cancelation.set(true);
                        System.out.println("User canceled processes");
                        promtIsActive.set(false);
                        continue;
                    default:
                        System.out.println("UNKNOWN USER COMMAND, \n Continuing computation...");
                        promtIsActive.set(false);
                        continue;
                }
            }

            //Verifying status
                String ResultStatus = "";
                if (future1.isDone() && future2.isDone() && future2.get().getResult() && future1.get().getResult()) {
                    ResultStatus += "The Final result is true";
                } else if ((future1.isDone() && !future1.get().getResult())||(future2.isDone() && !future2.get().getResult())) {
                    ResultStatus += "The Final result is false";
                } else if ( !future1.isDone() && !future2.isDone()){
                    ResultStatus += "Cannot define final result because both F and G were not complete";
                } else if(!future2.isDone()){
                    ResultStatus += "Cannot define final result because G was not complete";
                } else if(!future1.isDone()){
                    ResultStatus += "Cannot define final result because F was not complete";
                }

                System.out.println(ResultStatus);


        } catch (IOException | ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }

        // quiting
        for(Process process : processes) {
            if(process.isAlive())
                process.destroy();
        }
        executor.shutdownNow();
        keyLisMan.globalListenerManagerShutDown();
    }

    // KeyListener implementation
    public class EscapeListener implements NativeKeyListener
    {
        @Override
        public void nativeKeyTyped(NativeKeyEvent nativeKeyEvent) {

        }

        @Override
        public void nativeKeyPressed(NativeKeyEvent nativeKeyEvent) {
        }

        @Override
        public void nativeKeyReleased(NativeKeyEvent nativeKeyEvent) {

            if(nativeKeyEvent.getRawCode() == VK_ESCAPE) {
                if(!promtIsActive.get()) {
                    System.out.println("Exit by ESC");
                    cancelation.set(true);
                }
            }
        }
    }


}
