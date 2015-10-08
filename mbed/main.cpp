#include "mbed.h"
#include "Camera_LS_Y201.h"
#include "SDFileSystem.h"
#include "motordriver.h"
#include "HMC6352.h"
#define BUFFER_SIZE 10
#define USE_SDCARD 0
 
#if USE_SDCARD
#define FILENAME    "/sd/IMG_%04d.jpg"
SDFileSystem fs(p5, p6, p7, p8, "sd");
#else
#define FILENAME    "/local/IMG_%04d.jpg"
LocalFileSystem fs("local");
#endif
Camera_LS_Y201 cam1(p13, p14);
Serial rn42(p9,p10);
Motor right(p21, p22, p23, 1);
Motor left(p26, p25, p24, 1);
DigitalOut myled(LED1);
HMC6352 compass(p28, p27);
char sendRcvBuf[BUFFER_SIZE];
typedef struct work {
    FILE *fp;
} work_t;
 
work_t work;

void callback_func(int done, int total, uint8_t *buf, size_t siz) {
    fwrite(buf, siz, 1, work.fp);
 
    static int n = 0;
    int tmp = done * 100 / total;
    if (n != tmp) {
        n = tmp;
    }
}
 

int capture(Camera_LS_Y201 *cam, char *filename) {
    if (cam->takePicture() != 0) {
        return -1;
    }
    work.fp = fopen(filename, "wb");
    if (work.fp == NULL) {
        return -2;
    }
    if (cam->readJpegFileContent(callback_func) != 0) {
        fclose(work.fp);
        return -3;
    }
    fclose(work.fp);
    cam->stopTakingPictures();
    return 0;
}

void clearBuffer(char* arr, int size) {
    int i = 0;
    while(i < size) {
        arr[i++] = 0;
    }
}

float nextFloat() {
    char curr = rn42.getc();
    int i = 0;
    while(curr != '#') {
        sendRcvBuf[i++] = curr;
        while(!rn42.readable());
        curr = rn42.getc();
    }
    float ret = atof(sendRcvBuf);
    clearBuffer(sendRcvBuf, BUFFER_SIZE);
    return ret;
}

void sendFloat(float reading) {
    char * out = (char*)malloc(sizeof(float));
    sprintf(out, "%.2f", reading);
    int i = 0;
    while(i < strlen(out)) {
        rn42.putc(out[i++]);
    }
    rn42.putc('#');
    free(out);   
}

void sendInt(int reading) {
    char * out = (char*)malloc(sizeof(int));
    sprintf(out, "%d", reading);
    int i = 0;
    while(i < strlen(out)) {
        rn42.putc(out[i++]);
    }
    rn42.putc('#');
    free(out);   
}
const int SPEED_THRESH = 2.5;
const int TURN_THRESH = 2.5;
const int MAX_ANGLE_SPEED = 8;
const int MAX_ANGLE_TURN = 8;
void updateMotor(float speed, float turn) {
  //printf("speed: %.2f\n", speed);
  //printf("turn: %.2f\n", turn);
    float leftSpeed = 0.0;
    float rightSpeed = 0.0;
    
    if(speed < SPEED_THRESH && speed > -1*SPEED_THRESH) { //STAY STATIONARY
        if(turn > TURN_THRESH) { //SPIN LEFT
            leftSpeed = -1*(turn - TURN_THRESH)/(MAX_ANGLE_TURN-TURN_THRESH);
            rightSpeed = (turn - TURN_THRESH)/(MAX_ANGLE_TURN-TURN_THRESH);
        } else if(turn < -1 * TURN_THRESH) { //SPIN RIGHT
            leftSpeed = (turn - TURN_THRESH)/(MAX_ANGLE_TURN-TURN_THRESH);
            rightSpeed = -1*(turn - TURN_THRESH)/(MAX_ANGLE_TURN-TURN_THRESH);
        } else {
            left.stop(1);
            right.stop(1);
            leftSpeed = 0;
            rightSpeed = 0;
        }
    } else {
        if(speed > 0) { //GO FORWARD
            leftSpeed = (speed - SPEED_THRESH)/(MAX_ANGLE_SPEED-SPEED_THRESH);
            rightSpeed = (speed - SPEED_THRESH)/(MAX_ANGLE_SPEED-SPEED_THRESH);
        } else { //GO BACKWARDS
            leftSpeed = (speed + SPEED_THRESH)/(MAX_ANGLE_SPEED-SPEED_THRESH);
            rightSpeed = (speed + SPEED_THRESH)/(MAX_ANGLE_SPEED-SPEED_THRESH);
        }
        if(turn > TURN_THRESH) { //TURN LEFT
            leftSpeed = leftSpeed*.75;
            rightSpeed = rightSpeed*1.25;
        } else if (turn < -1*TURN_THRESH) { //TURN RIGHT
            leftSpeed = leftSpeed*1.25;
            rightSpeed = rightSpeed*.75;
        }
    }
    leftSpeed = leftSpeed > 1.0 ? 1.0 : leftSpeed;
    leftSpeed = leftSpeed < -1.0 ? -1.0 : leftSpeed;
    rightSpeed = rightSpeed > 1.0 ? 1.0 : rightSpeed;
    rightSpeed = rightSpeed < -1.0 ? -1.0 : rightSpeed;
//    sendFloat(leftSpeed);
//    sendFloat(rightSpeed);
    left.speed(leftSpeed);
    right.speed(rightSpeed);
}

void brakeMotor() {
    left.stop(1);
    right.stop(1);
}
int main(void) {
    rn42.baud(115200);
    rn42.putc('S');
    rn42.putc('#');
//    char sendRcvBuf[BUFFER_SIZE];
    int cnt = 0;
    
  //printf("Camera module\n");
  //printf("Resetting...\n");
    wait(1);
    if (cam1.reset() == 0) {
      //printf("Reset OK.\n");
    } else {
      //printf("Reset fail.\n");
      //error("Reset fail.");
    }
    wait(1);
    rn42.putc('R');
    rn42.putc('#');
    float speed = 0.0;
    float turn = 0.0;
    while(1) {
        if(rn42.readable()) {
            char byte = rn42.getc();
          //printf("Received: %c \n",byte);
            switch(byte) {
                case 'M': //Move command received; numbers to follow
                    while(!rn42.readable());
                    speed = nextFloat();
                    while(!rn42.readable());
                    turn = nextFloat(); 
                    updateMotor(speed, turn);
                    rn42.putc('D'); //precursor for Direction
                    sendFloat(compass.sample() / 10.0); //send compass reading             
                    break;
                case 'C':
                    brakeMotor();
                    char fname[64];
                    snprintf(fname, sizeof(fname) - 1, FILENAME, cnt);
                    int r = capture(&cam1, fname);
                    if (r == 0) {
                      //printf("[%04d]:OK.\n", cnt);
                    } else {
                      //printf("[%04d]:NG. (code=%d)\n", cnt, r);
                      //error("Failure.");
                    }
                    updateMotor(speed, turn);
                    
                    //char img[1024];
//                    FILE *f = NULL;
//                    f = fopen(fname, "r");
//                    if(f==NULL) {
//                     //printf("Problem opening file for reading\r\n");
//                    }
//                  //printf("about to send image");
//                    // read the file and dump it as hex
//                    size_t br;
//                    int n = 0;
//                    bool flag = false;
//                    fseek(f, 0, SEEK_END); // seek to end of file
//                    int size = ftell(f); // get current file pointer
//                    fseek(f, 0, SEEK_SET); // seek back to beginning of file
                    rn42.putc('P');
                    sendInt(0);
//                  //printf("size: %d\n", size);
//                    while((br=fread(img,1,1024,f))!=0) {
//                      //printf("Read %d bytes\r\n",br);
//                        for(size_t i=0; i<br; i++) {
//                            if(n==0) printf("start: %x %x\n",img[i],img[i+1]);
//                            rn42.putc(img[i]);
//                            if(i > 0 && img[i-1] == 0xFF && img[i] == 0xD9) {
//                            //printf("spot of last byte: %d\n",n);
//                              flag = true;
//                            }
//                            if(i < 1024 && img[i] == 0xFF && img[i+1] == 0xD8) {
//                            //printf("spot of first byte: %d\n",n);
//                            }
//                            n++;
//                            if(flag) {
//                              //printf("byte after: %x\n",img[i]);
//                            }
//                        }
//                    }
//                    
//                    // determine whether we reached EOF or not
//                    if(!feof(f)&&ferror(f)) {
//                     //printf("Did not complete reading file, file error\r\n");
//                    } else {
//                     //printf("File read successfully\r\n");
//                    }   
//                    fclose(f);
                    cnt++;
                    break;
                    
            }
        }
    }
}