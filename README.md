# Simple-TCP-chatroom-using-Java
I've built a simple TCP chatroom to which unlimited users can connect and have a chat in their terminal, *This is an absolute raw TCP chat application made with java.*
**Disclaimer** - This is only made just for the sake of learning server sockets and multithreading in java and YES there are no security measures as well as chat logging...

Technologies / libraries: 
1. Java.net and server sockets.
2. Java multithreading.

1. To run this first download both the files.
2. Run the Server class in a seperate terminal or terminal of any IDE.
3. Open two extra terminals (Powershell/CMD for windows, respective terminal for linux and MacOS).
4. Run the Client class seperatly.
5. That's it you have two Clients sitting in a localhost chatroom, simulating typical two person chatroom experience.

Features/commands:
1. /nick - for changing nickname of Client mid convo
2. /quit - for leaving the convo, this kills the Client class, and displays "[nickname] left the chat" message to all the others.

*If you want the chatroom to be active all the time, just deploy on any cloud servers like AWS, Google cloud server to host the Server.java file, and make sure to use tmux to keep the chat alive (Especially in google cloud server).
Finally change the address in the Client.java file to your respective host address and that's it.*

**Remember** - anyone with the Client.java file with your host address can join the chatroom.
