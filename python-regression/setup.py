from setuptools import setup

setup(name='IRI_PyRegression',
    description='Regression testing for IRI using Aloe',
    url='https://github.com/DyrellC/iri_regression_tests',
    author='DyrellC',
    packages=['util','tests'],
    install_requires=[
        'pyota',
        'aloe',
        'pyyaml'    
    ]       
    )