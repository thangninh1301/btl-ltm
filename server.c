#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <pthread.h>
#include <sys/types.h>
#include <signal.h>
#include <stdbool.h>

#define MAX_CLIENTS 50
#define BUFFER_SZ 2048

static _Atomic unsigned int cli_count = 0;
static int uid = 10;

typedef struct{
  struct sockaddr_in address;
  int sockfd;
  int uid;
  char channel[32];
  bool publisher;
} client_t;

client_t *clients[MAX_CLIENTS];

pthread_mutex_t clients_mutex = PTHREAD_MUTEX_INITIALIZER;

void queue_add(client_t *cl){
  pthread_mutex_lock(&clients_mutex);

  for(int i=0; i < MAX_CLIENTS; ++i){
    if(!clients[i]){
      clients[i] = cl;
      break;
    }
  }

  pthread_mutex_unlock(&clients_mutex);
}

void queue_remove(int uid){
  pthread_mutex_lock(&clients_mutex);

  for(int i=0; i < MAX_CLIENTS; ++i){
    if(clients[i]){
      if(clients[i]->uid == uid){
        clients[i] = NULL;
        break;
      }
    }
  }

  pthread_mutex_unlock(&clients_mutex);
}

void send_message(char *s, int uid, char *channel){
  pthread_mutex_lock(&clients_mutex);


  for(int i=0; i<MAX_CLIENTS; ++i){
    if(clients[i]){
      if(clients[i]->uid != uid && strcmp(clients[i]->channel, channel) == 0 && !clients[i]->publisher){
        if(write(clients[i]->sockfd, s, strlen(s)) < 0){
          perror("write to socket failed");
          break;
        }
      }
    }
  }

  pthread_mutex_unlock(&clients_mutex);
}

void *handle_client(void *arg){
  char buff_out[BUFFER_SZ];
  char register_string[34];
  char* channel = register_string + 2;
  int leave_flag = 0;

  cli_count++;
  client_t *cli = (client_t *)arg;

  // channel
  if(recv(cli->sockfd, register_string, 34, 0) <= 0 || strlen(register_string) <  2 || strlen(register_string) >= 34-1){
    printf("Didn't enter the channel.\n");
    leave_flag = 1;
  } else{
    strcpy(cli->channel, channel);
    if (register_string[0] == '1') {
      cli->publisher = true;
      printf("publisher has joined with channel %s.\n", cli->channel);
      send_message("publisher has joined\n", cli->uid, cli->channel);
    } else {
      cli->publisher = false;
      printf("subscriber has joined with channel %s.\n", cli->channel);
      send_message("subscriber has joined\n", cli->uid, cli->channel);
    }
  }

  bzero(buff_out, BUFFER_SZ);

  while(1){
    if (leave_flag) {
      break;
    }

    int receive = recv(cli->sockfd, buff_out, BUFFER_SZ, 0);
    if (receive > 0){
      if(strlen(buff_out) > 0){
        send_message(buff_out, cli->uid, cli->channel);

        printf("%s -> %s\n", buff_out, cli->channel);
      }
    } else if (receive == 0 || strcmp(buff_out, "exit") == 0){
      sprintf(buff_out, "%s has left\n", cli->channel);
      printf("%s", buff_out);
      send_message(buff_out, cli->uid, cli->channel);
      leave_flag = 1;
    } else {
      printf("ERROR: -1\n");
      leave_flag = 1;
    }

    bzero(buff_out, BUFFER_SZ);
  }

    // Del client and thread
    close(cli->sockfd);
    queue_remove(cli->uid);
    free(cli);
    cli_count--;
    pthread_detach(pthread_self());

  return NULL;
}

int main(int argc, char **argv){
  if(argc != 2){
    printf("Usage: %s <port>\n", argv[0]);
    return EXIT_FAILURE;
  }

  char *ip = "127.0.0.1";
  int port = atoi(argv[1]);
  int option = 1;
  int listenfd = 0, connfd = 0;
    struct sockaddr_in serv_addr;
    struct sockaddr_in cli_addr;
    pthread_t tid;
    
    listenfd = socket(AF_INET, SOCK_STREAM, 0);
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_addr.s_addr = inet_addr(ip);
    serv_addr.sin_port = htons(port);

  // Bind
    if(bind(listenfd, (struct sockaddr*)&serv_addr, sizeof(serv_addr)) < 0) {
        perror("Binding failed");
        return EXIT_FAILURE;
    }

    // Listen
    if (listen(listenfd, 10) < 0) {
        perror("Listening failed");
        return EXIT_FAILURE;
    }

  printf("WELCOME TO BROKER SERVER\n");

  while(1){
    socklen_t clilen = sizeof(cli_addr);
    connfd = accept(listenfd, (struct sockaddr*)&cli_addr, &clilen);
        // check max client
    if((cli_count + 1) == MAX_CLIENTS){
      printf("Max clients reached. Rejected port: ");
      printf(":%d\n", cli_addr.sin_port);
      close(connfd);
      continue;
    }

    client_t *cli = (client_t *)malloc(sizeof(client_t));
    cli->address = cli_addr;
    cli->sockfd = connfd;
    cli->uid = uid++;

    queue_add(cli);
    pthread_create(&tid, NULL, &handle_client, (void*)cli);

    sleep(1);
  }

  return EXIT_SUCCESS;
}
