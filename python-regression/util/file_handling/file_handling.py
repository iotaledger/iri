class FileHandling():
        
    def make_file(self,file_name):
        return open(file_name,"w+")
            
    def open_file_read(self,file):
        return open(file,"r")    
    
    
    def close_file(self,file_name):
        try:
            file_name.close()
        except:
            print("Close File Failed")


        