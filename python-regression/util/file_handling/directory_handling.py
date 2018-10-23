
import os 
import shutil


class DirectoryHandling():

    def current_directory(self):
        cwd = os.getcwd()
        return cwd
    
    def change_directory(self,directory_path):
        os.chdir(directory_path)
        
        
    def make_directory(self,directory_path):
        exists = os.path.isdir(directory_path)
        if(exists):
            shutil.rmtree(directory_path, False, None)
            os.makedirs(directory_path)
        else:
            os.makedirs(directory_path)

        
    def make_and_enter(self,directory_path):
        exists = os.path.isdir(directory_path)
        if(exists):
            shutil.rmtree(directory_path, False, None)
            os.makedirs(directory_path)
            os.chdir(directory_path)
        else:
            os.makedirs(directory_path)
            os.chdir(directory_path)
    
    